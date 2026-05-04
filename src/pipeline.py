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


def _build_meta(post: dict, subreddit: str) -> PostMetadata:
    return PostMetadata(
        post_id=post["name"],
        title=post.get("title", ""),
        author=post.get("author", "[deleted]"),
        subreddit=post.get("subreddit", subreddit),
        score=post.get("ups", post.get("score", 0)),
        created_utc=post.get("created_utc", 0.0),
        permalink=post.get("permalink", ""),
    )


def _dedup_download(
    urls: list[str],
    tracker: PostTracker,
    tmp_dir: Path,
    *,
    skip_content_dedup: bool = False,
) -> tuple[list[tuple[str, Path, str]], set[str]]:
    """Download and SHA1-dedup a list of URLs. Returns (clean_items, failed_urls)."""
    downloaded = download_batch(urls, tmp_dir, is_processed=tracker.is_processed)
    downloaded_urls = {u for u, _ in downloaded}

    clean: list[tuple[str, Path, str]] = []
    for url, path in downloaded:
        sha1 = compute_file_sha1(path)
        if not skip_content_dedup and tracker.is_content_processed(sha1):
            logger.info("Skipping content duplicate (sha1=%s...): %s", sha1[:8], url)
            tracker.mark_processed(url)
            path.unlink(missing_ok=True)
        else:
            clean.append((url, path, sha1))

    failed = {url for url in urls if url not in downloaded_urls}
    return clean, failed


def _classify_and_partition(
    clean_downloaded: list[tuple[str, Path, str]],
    classifier: BaseClassifier,
    classify_workers: int | None,
    tracker: PostTracker,
) -> tuple[list, dict[str, str], int, bool]:
    """Classify images and split into memes vs. non-memes.

    Returns (meme_items, url_to_sha1, error_count, should_abort).
    should_abort is True when the claude CLI is missing.
    """
    downloaded_for_classify = [(url, path) for url, path, _ in clean_downloaded]
    results = classifier.classify_batch(downloaded_for_classify, max_workers=classify_workers)
    url_to_sha1 = {url: sha1 for url, _, sha1 in clean_downloaded}

    meme_items = []
    error_count = 0
    for result in results:
        if result.error:
            error_count += 1
            logger.warning("Skipping bloom indexing for %s (will retry): %s", result.url, result.error)
        elif result.is_meme:
            meme_items.append(result)
        else:
            tracker.mark_processed(result.url)
    tracker.flush()

    should_abort = (
        error_count > 0
        and error_count == len(results)
        and any(r.error == "claude_not_found" for r in results)
    )
    return meme_items, url_to_sha1, error_count, should_abort


def _commit_batch(
    meme_items: list,
    url_to_path: dict[str, Path],
    url_to_sha1: dict[str, str],
    tracker: PostTracker,
    repo_path: Path,
    batch_num: int,
    subreddit: str,
    url_to_meta: dict,
    locale: str,
) -> int:
    """Save and commit a list of meme results. Returns the number committed."""
    items_with_paths = [
        (result, url_to_path[result.url])
        for result in meme_items
        if result.url in url_to_path
    ]
    save_and_commit_batch(
        items_with_paths, repo_path, batch_num, subreddit,
        url_to_meta=url_to_meta, locale=locale,
    )
    for result, _ in items_with_paths:
        tracker.mark_processed(result.url)
        if result.url in url_to_sha1:
            tracker.mark_content_processed(url_to_sha1[result.url])
    tracker.flush()
    return len(items_with_paths)


# ---------------------------------------------------------------------------
# Batch mode: collect all URLs from all posts first, then process in chunks.
# ---------------------------------------------------------------------------

def _run_batch(
    posts: list[dict],
    tracker: PostTracker,
    classifier: BaseClassifier,
    *,
    subreddit: str,
    batch_size: int,
    dry_run: bool,
    min_comment_upvotes: int,
    classify_workers: int | None,
    repo_path: Path,
    tmp_dir: Path,
    locale: str,
    skip_content_dedup: bool = False,
) -> int:
    all_urls: list[str] = []
    seen_in_run: set[str] = set()
    url_to_meta: dict[str, PostMetadata] = {}

    for post in posts:
        post_id = post["name"]
        if tracker.is_processed(post_id):
            continue

        meta = _build_meta(post, subreddit)
        for url, comment_score in fetch_comment_images(post, min_comment_upvotes):
            if url in seen_in_run or tracker.is_processed(url):
                continue
            seen_in_run.add(url)
            all_urls.append(url)
            url_to_meta[url] = dataclasses.replace(meta, score=comment_score)
        tracker.mark_processed(post_id)

    logger.info("Fetched %d posts — %d new image URLs to process", len(posts), len(all_urls))

    if not all_urls:
        tracker.flush()
        logger.info("Nothing new to process.")
        return 0

    total_memes = 0

    for batch_num, batch_urls in enumerate(_chunks(all_urls, batch_size), start=1):
        logger.info("--- Batch %d: %d URLs ---", batch_num, len(batch_urls))

        clean_downloaded, failed_urls = _dedup_download(
            batch_urls, tracker, tmp_dir, skip_content_dedup=skip_content_dedup
        )
        for url in failed_urls:
            tracker.mark_processed(url)
        tracker.flush()

        if not clean_downloaded:
            continue

        meme_items, url_to_sha1, error_count, should_abort = _classify_and_partition(
            clean_downloaded, classifier, classify_workers, tracker
        )

        if should_abort:
            logger.error(
                "'claude' CLI not found — aborting pipeline. "
                "Install Claude Code and ensure it is in PATH."
            )
            return total_memes

        if error_count and error_count == len(clean_downloaded):
            logger.warning(
                "All %d items in batch %d failed classification — "
                "they will be retried next run.",
                error_count, batch_num,
            )

        if meme_items and not dry_run:
            url_to_path = {url: path for url, path, _ in clean_downloaded}
            total_memes += _commit_batch(
                meme_items, url_to_path, url_to_sha1, tracker,
                repo_path, batch_num, subreddit, url_to_meta, locale,
            )
        elif meme_items and dry_run:
            for result in meme_items:
                logger.info(
                    "[DRY RUN] Would save: %s/%s (%s)",
                    result.category, result.filename_slug, result.url,
                )

        for _, tmp_path, _ in clean_downloaded:
            try:
                tmp_path.unlink()
            except OSError:
                pass

    return total_memes


# ---------------------------------------------------------------------------
# Per-post mode: download and classify each post's images immediately.
# ---------------------------------------------------------------------------

def _run_per_post(
    posts: list[dict],
    tracker: PostTracker,
    classifier: BaseClassifier,
    *,
    subreddit: str,
    batch_size: int,
    dry_run: bool,
    min_comment_upvotes: int,
    classify_workers: int | None,
    repo_path: Path,
    tmp_dir: Path,
    locale: str,
    skip_content_dedup: bool = False,
) -> int:
    seen_in_run: set[str] = set()
    url_to_meta: dict[str, PostMetadata] = {}

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

        meta = _build_meta(post, subreddit)
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

        clean_downloaded, failed_urls = _dedup_download(
            post_urls, tracker, tmp_dir, skip_content_dedup=skip_content_dedup
        )
        for url in failed_urls:
            tracker.mark_processed(url)
        tracker.flush()

        if not clean_downloaded:
            continue

        meme_items, url_to_sha1, error_count, should_abort = _classify_and_partition(
            clean_downloaded, classifier, classify_workers, tracker
        )

        if should_abort:
            logger.error(
                "'claude' CLI not found — aborting pipeline. "
                "Install Claude Code and ensure it is in PATH."
            )
            return total_memes

        if error_count and error_count == len(clean_downloaded):
            logger.warning(
                "All %d items in post %s failed classification — "
                "they will be retried next run.",
                error_count, post_id,
            )

        url_to_path = {url: path for url, path, _ in clean_downloaded}
        for result in meme_items:
            pending_memes.append(result)
            pending_sha1[result.url] = url_to_sha1[result.url]
            pending_paths[result.url] = url_to_path[result.url]

        # Commit once enough memes have accumulated across posts.
        if len(pending_memes) >= batch_size and not dry_run:
            batch_num += 1
            total_memes += _commit_batch(
                pending_memes, pending_paths, pending_sha1, tracker,
                repo_path, batch_num, subreddit, url_to_meta, locale,
            )
            for r in pending_memes:
                try:
                    pending_paths[r.url].unlink()
                except OSError:
                    pass
            pending_memes.clear()
            pending_sha1.clear()
            pending_paths.clear()

        # Clean up tmp files for rejected/errored images; pending memes keep theirs until commit.
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
        total_memes += _commit_batch(
            pending_memes, pending_paths, pending_sha1, tracker,
            repo_path, batch_num, subreddit, url_to_meta, locale,
        )
        for r in pending_memes:
            try:
                pending_paths[r.url].unlink()
            except OSError:
                pass

    elif pending_memes and dry_run:
        for result in pending_memes:
            logger.info(
                "[DRY RUN] Would save: %s/%s (%s)",
                result.category, result.filename_slug, result.url,
            )
        for r in pending_memes:
            try:
                pending_paths[r.url].unlink()
            except OSError:
                pass

    if not total_memes:
        tracker.flush()
        logger.info("Nothing new to process.")

    return total_memes


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------

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
    per_post: bool = False,
    skip_content_dedup: bool = False,
) -> None:
    tracker = _build_tracker()
    _index_existing_memes(tracker, repo_path, force=rebuild_content_index)
    _classifier = classifier if classifier is not None else ClaudeClassifier()

    mode = "per-post" if per_post else "batch"
    logger.info(
        "Starting pipeline: r/%s limit=%d sort=%s timeframe=%s page=%d batch=%d dry_run=%s mode=%s",
        subreddit, limit, sort, timeframe, page, batch_size, dry_run, mode,
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

    tmp_dir = config.TMP_DIR
    tmp_dir.mkdir(parents=True, exist_ok=True)

    kwargs = dict(
        subreddit=subreddit,
        batch_size=batch_size,
        dry_run=dry_run,
        min_comment_upvotes=min_comment_upvotes,
        classify_workers=classify_workers,
        repo_path=repo_path,
        tmp_dir=tmp_dir,
        locale=locale,
        skip_content_dedup=skip_content_dedup,
    )

    if per_post:
        total_memes = _run_per_post(posts, tracker, _classifier, **kwargs)
    else:
        total_memes = _run_batch(posts, tracker, _classifier, **kwargs)

    logger.info("Pipeline complete. Total memes saved: %d", total_memes)
