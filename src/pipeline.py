import dataclasses
import datetime
import json
import logging
from pathlib import Path

import config
from src.classifiers import BaseClassifier, ClaudeClassifier
from src.downloader import compute_file_sha1, download_batch
from src.models import PostMetadata
from src.post_tracker import PostTracker
from src.saver import git_create_branch, save_and_commit_batch
from src.scraper import fetch_comment_images, fetch_posts, fetch_single_post

logger = logging.getLogger(__name__)


def _chunks(lst: list, n: int):
    for i in range(0, len(lst), n):
        yield lst[i : i + n]


def _load_posts_from_file(json_file: Path) -> list[dict]:
    with open(json_file) as f:
        data = json.load(f)
    if isinstance(data, list):
        data = data[0]
    children = data.get("data", {}).get("children", [])
    return [c["data"] for c in children if c.get("kind") == "t3"]


def _index_existing_memes(tracker: PostTracker, repo_path: Path, force: bool = False) -> None:
    if not force and tracker.metadata.get("sha1_indexed"):
        return
    memes_dir = repo_path / "memes"
    if not memes_dir.exists():
        tracker.metadata["sha1_indexed"] = True
        tracker.flush()
        return
    extensions = {".jpg", ".jpeg", ".png", ".gif", ".webp"}
    indexed = 0
    for img_path in memes_dir.rglob("*"):
        if img_path.suffix.lower() in extensions and img_path.is_file():
            tracker.mark_content_processed(compute_file_sha1(img_path))
            indexed += 1
    tracker.metadata["sha1_indexed"] = True
    tracker.flush()
    logger.info("Content-indexed %d existing memes", indexed)


def _build_tracker() -> PostTracker:
    tracker = PostTracker(
        config.BLOOM_FILTER_FILE,
        capacity=config.BLOOM_CAPACITY,
        error_rate=config.BLOOM_ERROR_RATE,
    )
    if not config.BLOOM_FILTER_FILE.exists() and config.LEGACY_STATE_FILE.exists():
        imported = tracker.migrate_from_state_json(config.LEGACY_STATE_FILE)
        if imported:
            tracker.flush()
            config.LEGACY_STATE_FILE.unlink()
            logger.info("Removed legacy %s after migration", config.LEGACY_STATE_FILE.name)
    return tracker


def run(
    subreddit: str = config.SUBREDDIT,
    limit: int = 100,
    repo_path: Path = config.REPO_PATH,
    batch_size: int = 10,
    dry_run: bool = False,
    from_file: Path | None = None,
    post_url: str | None = None,
    min_comment_upvotes: int = 0,
    sort: str = "hot",
    timeframe: str = "day",
    page: int = 1,
    rebuild_content_index: bool = False,
    classify_workers: int | None = None,
    classifier: BaseClassifier | None = None,
    create_branch: bool = True,
    locale: str = "en",
) -> None:
    tracker = _build_tracker()
    _index_existing_memes(tracker, repo_path, force=rebuild_content_index)
    _classifier = classifier if classifier is not None else ClaudeClassifier()

    logger.info(
        "Starting pipeline: r/%s limit=%d sort=%s timeframe=%s page=%d batch=%d dry_run=%s",
        subreddit, limit, sort, timeframe, page, batch_size, dry_run,
    )

    if create_branch and not dry_run:
        branch_name = f"memes/{subreddit}-{datetime.datetime.now().strftime('%Y%m%d-%H%M%S')}"
        if not git_create_branch(repo_path, branch_name):
            logger.error("Aborting: could not create branch %s", branch_name)
            return

    if post_url:
        posts = fetch_single_post(post_url)
    elif from_file:
        logger.info("Loading posts from file: %s", from_file)
        posts = _load_posts_from_file(from_file)
    else:
        posts = fetch_posts(subreddit, limit=limit, sort=sort, timeframe=timeframe, page=page)

    seen_in_run: set[str] = set()
    url_to_meta: dict[str, PostMetadata] = {}

    tmp_dir = config.TMP_DIR
    tmp_dir.mkdir(parents=True, exist_ok=True)

    pending_memes: list = []
    pending_sha1: dict[str, str] = {}
    pending_paths: dict[str, Path] = {}
    total_memes = 0
    batch_num = 0

    logger.info("Fetched %d posts — processing per-post", len(posts))

    for post in posts:
        post_id = post["name"]
        if tracker.is_processed(post_id):
            continue

        meta = PostMetadata(
            post_id=post_id,
            title=post.get("title", ""),
            author=post.get("author", "[deleted]"),
            subreddit=post.get("subreddit", subreddit),
            score=post.get("ups", post.get("score", 0)),
            created_utc=post.get("created_utc", 0.0),
            permalink=post.get("permalink", ""),
        )

        post_urls: list[str] = []
        for url, comment_score in fetch_comment_images(post, min_comment_upvotes):
            if url in seen_in_run or tracker.is_processed(url):
                continue
            seen_in_run.add(url)
            post_urls.append(url)
            url_to_meta[url] = dataclasses.replace(meta, score=comment_score)

        tracker.mark_processed(post_id)

        if not post_urls:
            continue

        logger.info("Post %s > %d image(s) found > starting classification", post_id, len(post_urls))

        downloaded = download_batch(post_urls, tmp_dir, is_processed=tracker.is_processed)

        # Filter content-identical images (same bytes, different URL) before classifying.
        clean_downloaded: list[tuple[str, Path, str]] = []
        for url, path in downloaded:
            sha1 = compute_file_sha1(path)
            if tracker.is_content_processed(sha1):
                logger.info("Skipping content duplicate (sha1=%s...): %s", sha1[:8], url)
                tracker.mark_processed(url)
                path.unlink(missing_ok=True)
            else:
                clean_downloaded.append((url, path, sha1))

        for url in post_urls:
            if not any(u == url for u, _ in downloaded):
                tracker.mark_processed(url)
        tracker.flush()

        if not clean_downloaded:
            continue

        downloaded_for_classify = [(url, path) for url, path, _ in clean_downloaded]
        results = _classifier.classify_batch(downloaded_for_classify, max_workers=classify_workers)

        url_to_sha1 = {url: sha1 for url, _, sha1 in clean_downloaded}
        url_to_path = dict(downloaded_for_classify)

        error_count = 0
        for result in results:
            if result.error:
                error_count += 1
                logger.warning(
                    "Skipping bloom indexing for %s (will retry): %s",
                    result.url, result.error,
                )
            elif result.is_meme:
                pending_memes.append(result)
                pending_sha1[result.url] = url_to_sha1[result.url]
                pending_paths[result.url] = url_to_path[result.url]
            else:
                tracker.mark_processed(result.url)
        tracker.flush()

        if error_count and error_count == len(results):
            if any(r.error == "claude_not_found" for r in results):
                logger.error(
                    "'claude' CLI not found — aborting pipeline. "
                    "Install Claude Code and ensure it is in PATH."
                )
                return
            logger.warning(
                "All %d items in this post failed classification — "
                "they will be retried next run.",
                error_count,
            )

        # Commit once enough memes have accumulated across posts.
        if len(pending_memes) >= batch_size and not dry_run:
            batch_num += 1
            items_with_paths = [(r, pending_paths[r.url]) for r in pending_memes]
            save_and_commit_batch(items_with_paths, repo_path, batch_num, subreddit, url_to_meta=url_to_meta, locale=locale)
            for result, _ in items_with_paths:
                tracker.mark_processed(result.url)
                tracker.mark_content_processed(pending_sha1[result.url])
                total_memes += 1
            tracker.flush()
            for r in pending_memes:
                try:
                    pending_paths[r.url].unlink()
                except OSError:
                    pass
            pending_memes.clear()
            pending_sha1.clear()
            pending_paths.clear()

        # Cleanup tmp files for rejected/errored images (pending memes keep their file until commit).
        pending_urls = {r.url for r in pending_memes}
        for url, tmp_path, _ in clean_downloaded:
            if url not in pending_urls:
                try:
                    tmp_path.unlink()
                except OSError:
                    pass

    # Flush any remaining memes that didn't fill a full batch.
    if pending_memes and not dry_run:
        batch_num += 1
        items_with_paths = [(r, pending_paths[r.url]) for r in pending_memes]
        save_and_commit_batch(items_with_paths, repo_path, batch_num, subreddit, url_to_meta=url_to_meta, locale=locale)
        for result, _ in items_with_paths:
            tracker.mark_processed(result.url)
            tracker.mark_content_processed(pending_sha1[result.url])
            total_memes += 1
        tracker.flush()
        for r in pending_memes:
            try:
                pending_paths[r.url].unlink()
            except OSError:
                pass

    if pending_memes and dry_run:
        for result in pending_memes:
            logger.info("[DRY RUN] Would save: %s/%s (%s)", result.category, result.filename_slug, result.url)
        for r in pending_memes:
            try:
                pending_paths[r.url].unlink()
            except OSError:
                pass

    if not total_memes and not pending_memes:
        tracker.flush()
        logger.info("Nothing new to process.")

    logger.info("Pipeline complete. Total memes saved: %d", total_memes)
