import json
import logging
from pathlib import Path

import config
from src.classifiers import BaseClassifier, ClaudeClassifier
from src.downloader import compute_file_sha1, download_batch
from src.models import PostMetadata
from src.post_tracker import PostTracker
from src.saver import save_and_commit_batch
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
) -> None:
    tracker = _build_tracker()
    _index_existing_memes(tracker, repo_path, force=rebuild_content_index)
    _classifier = classifier if classifier is not None else ClaudeClassifier()

    logger.info(
        "Starting pipeline: r/%s limit=%d sort=%s timeframe=%s page=%d batch=%d dry_run=%s",
        subreddit, limit, sort, timeframe, page, batch_size, dry_run,
    )

    if post_url:
        posts = fetch_single_post(post_url)
    elif from_file:
        logger.info("Loading posts from file: %s", from_file)
        posts = _load_posts_from_file(from_file)
    else:
        posts = fetch_posts(subreddit, limit=limit, sort=sort, timeframe=timeframe, page=page)

    all_urls: list[str] = []
    seen_in_run: set[str] = set()
    url_to_meta: dict[str, PostMetadata] = {}

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

        for url in fetch_comment_images(post, min_comment_upvotes):
            if url in seen_in_run or tracker.is_processed(url):
                continue
            seen_in_run.add(url)
            all_urls.append(url)
            url_to_meta[url] = meta

        tracker.mark_processed(post_id)

    logger.info("Fetched %d posts — %d new image URLs to process", len(posts), len(all_urls))

    if not all_urls:
        tracker.flush()
        logger.info("Nothing new to process.")
        return

    tmp_dir = config.TMP_DIR
    tmp_dir.mkdir(parents=True, exist_ok=True)

    total_memes = 0

    for batch_num, batch_urls in enumerate(_chunks(all_urls, batch_size), start=1):
        logger.info("--- Batch %d: %d URLs ---", batch_num, len(batch_urls))

        downloaded = download_batch(batch_urls, tmp_dir, is_processed=tracker.is_processed)

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

        for url in batch_urls:
            if not any(u == url for u, _ in downloaded):
                tracker.mark_processed(url)
        tracker.flush()

        if not clean_downloaded:
            continue

        downloaded_for_classify = [(url, path) for url, path, _ in clean_downloaded]
        results = _classifier.classify_batch(downloaded_for_classify, max_workers=classify_workers)

        url_to_sha1 = {url: sha1 for url, _, sha1 in clean_downloaded}

        meme_items: list = []
        error_count = 0
        for result in results:
            if result.error:
                error_count += 1
                logger.warning(
                    "Skipping bloom indexing for %s (will retry): %s",
                    result.url, result.error,
                )
            elif result.is_meme:
                meme_items.append(result)
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
                "All %d items in batch failed classification — "
                "they will be retried next run.",
                error_count,
            )

        if meme_items and not dry_run:
            url_to_path = dict(downloaded_for_classify)
            items_with_paths = [
                (result, url_to_path[result.url])
                for result in meme_items
                if result.url in url_to_path
            ]

            saved = save_and_commit_batch(items_with_paths, repo_path, batch_num, subreddit, url_to_meta=url_to_meta)

            for result, _ in items_with_paths:
                tracker.mark_processed(result.url)
                if result.url in url_to_sha1:
                    tracker.mark_content_processed(url_to_sha1[result.url])
                total_memes += 1
            tracker.flush()

            del saved

        elif meme_items and dry_run:
            for result in meme_items:
                logger.info("[DRY RUN] Would save: %s/%s (%s)", result.category, result.filename_slug, result.url)

        for _, tmp_path, _ in clean_downloaded:
            try:
                tmp_path.unlink()
            except OSError:
                pass

    logger.info("Pipeline complete. Total memes saved: %d", total_memes)
