import json
import logging
from pathlib import Path

import config
from src.classifier import classify_batch
from src.downloader import download_batch
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
) -> None:
    tracker = _build_tracker()

    logger.info("Starting pipeline: r/%s limit=%d batch=%d dry_run=%s", subreddit, limit, batch_size, dry_run)

    if post_url:
        posts = fetch_single_post(post_url)
    elif from_file:
        logger.info("Loading posts from file: %s", from_file)
        posts = _load_posts_from_file(from_file)
    else:
        posts = fetch_posts(subreddit, limit=limit)

    all_urls: list[str] = []
    seen_in_run: set[str] = set()

    for post in posts:
        post_id = post["name"]
        if tracker.is_processed(post_id):
            continue

        if min_comment_upvotes > 0:
            for url in fetch_comment_images(post, min_comment_upvotes):
                if url in seen_in_run or tracker.is_processed(url):
                    continue
                seen_in_run.add(url)
                all_urls.append(url)

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

        for url in batch_urls:
            if not any(u == url for u, _ in downloaded):
                tracker.mark_processed(url)
        tracker.flush()

        if not downloaded:
            continue

        results = classify_batch(downloaded)

        meme_items: list = []
        for result in results:
            if result.is_meme and not result.error:
                meme_items.append(result)
            else:
                tracker.mark_processed(result.url)
        tracker.flush()

        if meme_items and not dry_run:
            url_to_path = dict(downloaded)
            items_with_paths = [
                (result, url_to_path[result.url])
                for result in meme_items
                if result.url in url_to_path
            ]

            saved = save_and_commit_batch(items_with_paths, repo_path, batch_num, subreddit)

            for result, _ in items_with_paths:
                tracker.mark_processed(result.url)
                total_memes += 1
            tracker.flush()

            del saved

        elif meme_items and dry_run:
            for result in meme_items:
                logger.info("[DRY RUN] Would save: %s/%s (%s)", result.category, result.filename_slug, result.url)

        for _, tmp_path in downloaded:
            try:
                tmp_path.unlink()
            except OSError:
                pass

    logger.info("Pipeline complete. Total memes saved: %d", total_memes)
