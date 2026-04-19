import hashlib
import logging
from pathlib import Path
from urllib.parse import urlparse

import requests

import config

logger = logging.getLogger(__name__)

_EXT_FROM_CONTENT_TYPE = {
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
    "image/gif": ".gif",
    "image/webp": ".webp",
}


def url_to_tmp_filename(url: str) -> str:
    digest = hashlib.sha1(url.encode()).hexdigest()[:12]
    parsed = urlparse(url)
    path_ext = "." + parsed.path.rsplit(".", 1)[-1].lower() if "." in parsed.path else ""
    ext = path_ext if path_ext in config.SUPPORTED_EXTENSIONS else ".jpg"
    return f"{digest}{ext}"


def download_image(url: str, tmp_dir: Path, session: requests.Session = None) -> Path | None:
    if session is None:
        session = requests.Session()

    dest = tmp_dir / url_to_tmp_filename(url)
    if dest.exists():
        logger.debug("Already downloaded: %s", dest.name)
        return dest

    try:
        resp = session.get(url, stream=True, timeout=20, headers={"User-Agent": config.REDDIT_USER_AGENT})
        resp.raise_for_status()

        content_type = resp.headers.get("Content-Type", "").split(";")[0].strip()
        if not content_type.startswith("image/"):
            logger.debug("Skipping non-image content-type %s for %s", content_type, url)
            return None

        ext = _EXT_FROM_CONTENT_TYPE.get(content_type)
        if ext and not dest.name.endswith(ext):
            dest = dest.with_suffix(ext)

        size = 0
        with open(dest, "wb") as f:
            for chunk in resp.iter_content(chunk_size=8192):
                size += len(chunk)
                if size > config.MAX_IMAGE_SIZE_BYTES:
                    logger.warning("Image too large (>%dMB), skipping: %s", config.MAX_IMAGE_SIZE_BYTES // 1024 // 1024, url)
                    dest.unlink(missing_ok=True)
                    return None
                f.write(chunk)

        logger.debug("Downloaded %s → %s (%d KB)", url, dest.name, size // 1024)
        return dest

    except requests.RequestException as e:
        logger.warning("Download failed for %s: %s", url, e)
        dest.unlink(missing_ok=True)
        return None


def download_batch(
    urls: list[str],
    tmp_dir: Path,
    already_processed: set[str],
) -> list[tuple[str, Path]]:
    tmp_dir.mkdir(parents=True, exist_ok=True)
    session = requests.Session()
    results = []

    for url in urls:
        if url in already_processed:
            logger.debug("Skipping already-processed: %s", url)
            continue
        path = download_image(url, tmp_dir, session)
        if path:
            results.append((url, path))

    return results
