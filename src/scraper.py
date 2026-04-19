import logging
import time
from urllib.parse import urlparse, urlunparse

import requests

import config

logger = logging.getLogger(__name__)


def _make_session() -> requests.Session:
    session = requests.Session()
    session.headers.update({"User-Agent": config.REDDIT_USER_AGENT})
    return session


def _get(url: str, session: requests.Session, params: dict = None) -> dict:
    time.sleep(config.REQUEST_DELAY)
    for attempt in range(3):
        try:
            resp = session.get(url, params=params, timeout=15)
            resp.raise_for_status()
            return resp.json()
        except requests.HTTPError as e:
            if e.response.status_code == 429:
                wait = 5 * (2 ** attempt)
                logger.warning("Rate limited, waiting %ds", wait)
                time.sleep(wait)
            elif e.response.status_code == 403:
                raise RuntimeError(
                    f"Reddit returned 403 Forbidden for {url}. "
                    "Reddit may be blocking this environment. "
                    "Try running from a normal internet connection."
                ) from e
            elif e.response.status_code >= 500:
                time.sleep(2 * (attempt + 1))
            else:
                raise
        except (requests.ConnectionError, requests.Timeout):
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Failed to fetch {url} after 3 attempts")


def _clean_url(url: str) -> str:
    parsed = urlparse(url)
    return urlunparse(parsed._replace(query="", fragment=""))


def extract_image_urls(post: dict) -> list[str]:
    urls = []
    direct_url = post.get("url", "")
    parsed = urlparse(direct_url)

    if parsed.netloc in config.IMAGE_HOSTS:
        ext = "." + direct_url.rsplit(".", 1)[-1].lower().split("?")[0] if "." in direct_url else ""
        if ext in config.SUPPORTED_EXTENSIONS:
            urls.append(_clean_url(direct_url))

    preview = post.get("preview", {})
    images = preview.get("images", [])
    for img in images:
        source = img.get("source", {})
        src_url = source.get("url", "")
        if src_url:
            cleaned = _clean_url(src_url)
            ext = "." + cleaned.rsplit(".", 1)[-1].lower() if "." in cleaned else ""
            if ext in config.SUPPORTED_EXTENSIONS:
                urls.append(cleaned)

    media_metadata = post.get("media_metadata", {})
    for item in media_metadata.values():
        if item.get("e") == "Image":
            s = item.get("s", {})
            u = s.get("u", "")
            if u:
                cleaned = _clean_url(u)
                urls.append(cleaned)

    return list(dict.fromkeys(urls))


def fetch_posts(subreddit: str, limit: int = 100) -> list[dict]:
    session = _make_session()
    url = f"{config.REDDIT_BASE_URL}/r/{subreddit}/.json"
    posts = []
    after = None

    while len(posts) < limit:
        page_size = min(25, limit - len(posts))
        params = {"limit": page_size}
        if after:
            params["after"] = after

        logger.info("Fetching r/%s page (after=%s, collected=%d/%d)", subreddit, after, len(posts), limit)
        try:
            data = _get(url, session, params=params)
        except RuntimeError as e:
            logger.error("Stopping pagination: %s", e)
            break

        children = data.get("data", {}).get("children", [])
        if not children:
            break

        for child in children:
            if child.get("kind") == "t3":
                posts.append(child["data"])

        after = data.get("data", {}).get("after")
        if not after:
            break

    logger.info("Fetched %d posts from r/%s", len(posts), subreddit)
    return posts
