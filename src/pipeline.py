import json
import logging
import os
from datetime import datetime, timezone
from pathlib import Path

import config
from src.classifier import classify_batch
from src.downloader import download_batch
from src.saver import save_and_commit_batch
from src.scraper import extract_image_urls, fetch_posts

logger = logging.getLogger(__name__)


class StateManager:
    def __init__(self, state_file: Path):
        self._path = state_file
        self._data: dict = {
            "version": 2,
            "processed_post_ids": {},
            "newest_post_fullname": None,
            "processed_urls": {},
        }

    def _migrate(self) -> None:
        if self._data.get("version", 1) < 2:
            self._data.setdefault("processed_post_ids", {})
            self._data.setdefault("newest_post_fullname", None)
            self._data["version"] = 2

    def load(self) -> None:
        if self._path.exists():
            try:
                with open(self._path) as f:
                    loaded = json.load(f)
                self._data = loaded
                self._migrate()
            except (json.JSONDecodeError, OSError) as e:
                logger.warning("Could not load state file: %s — starting fresh", e)

    def save(self) -> None:
        self._data["last_run"] = datetime.now(timezone.utc).isoformat()
        tmp = self._path.with_suffix(".json.tmp")
        with open(tmp, "w") as f:
            json.dump(self._data, f, indent=2)
        os.replace(tmp, self._path)

    # --- URL-level tracking ---

    def is_processed(self, url: str) -> bool:
        return url in self._data["processed_urls"]

    def get_processed_urls(self) -> set[str]:
        return set(self._data["processed_urls"].keys())

    def mark_saved(self, url: str, category: str, filename: str) -> None:
        self._data["processed_urls"][url] = {
            "status": "saved",
            "category": category,
            "filename": filename,
        }

    def mark_not_meme(self, url: str) -> None:
        self._data["processed_urls"][url] = {"status": "not_meme"}

    def mark_error(self, url: str, error: str) -> None:
        self._data["processed_urls"][url] = {"status": "error", "error": error}

    # --- Post-level tracking ---

    def is_post_processed(self, post_id: str) -> bool:
        return post_id in self._data["processed_post_ids"]

    def get_processed_post_ids(self) -> set[str]:
        return set(self._data["processed_post_ids"].keys())

    def mark_post_processed(self, post_id: str, subreddit: str, image_url_count: int) -> None:
        self._data["processed_post_ids"][post_id] = {
            "subreddit": subreddit,
            "processed_at": datetime.now(timezone.utc).isoformat(),
            "image_url_count": image_url_count,
        }

    def get_newest_post_fullname(self) -> str | None:
        return self._data.get("newest_post_fullname")

    def update_newest_post_fullname(self, fullname: str) -> None:
        self._data["newest_post_fullname"] = fullname


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


def run(
    subreddit: str = config.SUBREDDIT,
    limit: int = 100,
    repo_path: Path = config.REPO_PATH,
    batch_size: int = 10,
    dry_run: bool = False,
    from_file: Path | None = None,
) -> None:
    state = StateManager(config.STATE_FILE)
    state.load()
    processed = state.get_processed_urls()

    logger.info("Starting pipeline: r/%s limit=%d batch=%d dry_run=%s", subreddit, limit, batch_size, dry_run)

    if from_file:
        logger.info("Loading posts from file: %s", from_file)
        posts = _load_posts_from_file(from_file)
    else:
        posts = fetch_posts(
            subreddit,
            limit=limit,
            known_post_ids=state.get_processed_post_ids(),
            before_fullname=state.get_newest_post_fullname(),
        )

    all_urls: list[str] = []
    newest_seen_fullname: str | None = None

    for i, post in enumerate(posts):
        post_id = post["name"]
        if i == 0:
            newest_seen_fullname = post_id

        post_urls = []
        for url in extract_image_urls(post):
            if url not in processed and url not in all_urls:
                all_urls.append(url)
                post_urls.append(url)

        state.mark_post_processed(post_id, subreddit, len(post_urls))

    logger.info(
        "Fetched %d posts — %d new image URLs to process (%d posts already known)",
        len(posts), len(all_urls), len(state.get_processed_post_ids()) - len(posts),
    )

    if newest_seen_fullname:
        state.update_newest_post_fullname(newest_seen_fullname)

    if not all_urls:
        state.save()
        logger.info("Nothing new to process.")
        return

    tmp_dir = config.TMP_DIR
    tmp_dir.mkdir(exist_ok=True)

    total_memes = 0

    for batch_num, batch_urls in enumerate(_chunks(all_urls, batch_size), start=1):
        logger.info("--- Batch %d: %d URLs ---", batch_num, len(batch_urls))

        downloaded = download_batch(batch_urls, tmp_dir, already_processed=processed)

        for url in batch_urls:
            if not any(u == url for u, _ in downloaded):
                state.mark_error(url, "download_failed")
        state.save()

        if not downloaded:
            continue

        results = classify_batch(downloaded)

        meme_items: list = []
        for result in results:
            if result.error:
                state.mark_error(result.url, result.error)
            elif not result.is_meme:
                state.mark_not_meme(result.url)
            else:
                meme_items.append(result)
            state.save()

        if meme_items and not dry_run:
            items_with_paths = []
            url_to_path = dict(downloaded)
            for result in meme_items:
                path = url_to_path.get(result.url)
                if path:
                    items_with_paths.append((result, path))

            saved = save_and_commit_batch(items_with_paths, repo_path, batch_num, subreddit)

            for result, saved_path in zip(
                [r for r, _ in items_with_paths], saved
            ):
                state.mark_saved(result.url, result.category, str(saved_path.relative_to(repo_path)))
                total_memes += 1
            state.save()

        elif meme_items and dry_run:
            for result in meme_items:
                logger.info("[DRY RUN] Would save: %s/%s (%s)", result.category, result.filename_slug, result.url)

        for _, tmp_path in downloaded:
            try:
                tmp_path.unlink()
            except OSError:
                pass

    logger.info("Pipeline complete. Total memes saved: %d", total_memes)
