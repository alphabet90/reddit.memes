import logging
import re
from pathlib import Path

from src.downloader import compute_file_sha1
from src.post_tracker import PostTracker

logger = logging.getLogger(__name__)

_FRONTMATTER_RE = re.compile(r"^---\n(.*?)\n---", re.DOTALL)
_POST_ID_RE = re.compile(r"/comments/([a-z0-9]+)/")
_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".webp"}


def _parse_frontmatter(text: str, keys: set[str]) -> dict[str, str]:
    m = _FRONTMATTER_RE.search(text)
    if not m:
        return {}
    result = {}
    for line in m.group(1).splitlines():
        for key in keys:
            if line.startswith(f"{key}:"):
                result[key] = line[len(key) + 1 :].strip().strip("\"'")
                break
    return result


def restore_bloom_from_memes(repo_path: Path, tracker: PostTracker) -> int:
    """Rebuild the bloom filter by reading .mdx sidecar files from memes/.

    For each saved meme, marks its source_url, Reddit post ID (from post_url),
    and image SHA1 as processed. Returns the number of .mdx files processed.
    """
    memes_dir = repo_path / "memes"
    if not memes_dir.exists():
        logger.warning("memes/ directory not found at %s", memes_dir)
        return 0

    count = 0
    for mdx_path in sorted(memes_dir.rglob("*.mdx")):
        try:
            text = mdx_path.read_text(encoding="utf-8")
        except OSError as e:
            logger.warning("Could not read %s: %s", mdx_path, e)
            continue

        fields = _parse_frontmatter(text, {"source_url", "post_url"})

        source_url = fields.get("source_url", "")
        if source_url:
            tracker.mark_processed(source_url)

        post_url = fields.get("post_url", "")
        if post_url:
            m = _POST_ID_RE.search(post_url)
            if m:
                tracker.mark_processed(f"t3_{m.group(1)}")

        for ext in _IMAGE_EXTENSIONS:
            img_path = mdx_path.with_suffix(ext)
            if img_path.exists():
                sha1 = compute_file_sha1(img_path)
                tracker.mark_content_processed(sha1)
                break

        count += 1
        if count % 100 == 0:
            logger.info("Restored %d memes so far...", count)

    tracker.flush()
    logger.info("Bloom filter restored from %d memes in %s", count, memes_dir)
    return count
