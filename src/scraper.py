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
                wait = 360 * (2 ** attempt)
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
    import html
    url = html.unescape(url)
    parsed = urlparse(url)
    if parsed.netloc in ("preview.redd.it", "external-preview.redd.it"):
        return urlunparse(parsed._replace(fragment=""))
    return urlunparse(parsed._replace(query="", fragment=""))


def extract_image_urls_from_comment(comment: dict) -> list[str]:
    urls = []
    media_metadata = comment.get("media_metadata", {})
    for item in media_metadata.values():
        if item.get("status") != "valid" or item.get("e") != "Image":
            continue
        u = item.get("s", {}).get("u", "")
        if not u:
            continue
        cleaned = _clean_url(u)
        parsed = urlparse(cleaned)
        if parsed.netloc not in config.IMAGE_HOSTS:
            logger.debug("Skipping image (host not whitelisted): %s", parsed.netloc)
            continue
        ext = "." + parsed.path.rsplit(".", 1)[-1].lower() if "." in parsed.path else ""
        if ext not in config.SUPPORTED_EXTENSIONS:
            filename = parsed.path.rsplit("/", 1)[-1]
            logger.debug("Skipping image (unsupported extension %r): %s", ext, filename)
            continue
        urls.append(cleaned)
    return list(dict.fromkeys(urls))


def _flatten_comments(children: list) -> list[dict]:
    result = []
    for child in children:
        if child.get("kind") != "t1":
            continue
        data = child["data"]
        result.append(data)
        replies = data.get("replies", "")
        if isinstance(replies, dict):
            result.extend(_flatten_comments(replies.get("data", {}).get("children", [])))
    return result


def fetch_comment_images(post: dict, min_upvotes: int) -> list[tuple[str, int]]:
    subreddit = post["subreddit"]
    post_id = post["id"]
    url = f"{config.REDDIT_BASE_URL}/r/{subreddit}/comments/{post_id}/.json"
    session = _make_session()
    try:
        data = _get(url, session)
        children = data[1].get("data", {}).get("children", [])
    except Exception as e:
        logger.warning("Could not fetch comments for post %s: %s", post_id, e)
        return []

    comments = _flatten_comments(children)
    seen: dict[str, int] = {}
    for comment in comments:
        comment_score = comment.get("ups", 0)
        if comment_score >= min_upvotes:
            for img_url in extract_image_urls_from_comment(comment):
                if img_url not in seen:
                    seen[img_url] = comment_score

    result = list(seen.items())
    logger.info("Post %s: %d comment image(s) found with >= %d upvotes", post_id, len(result), min_upvotes)
    return result


def _to_old_reddit(url: str) -> str:
    parsed = urlparse(url)
    return urlunparse(parsed._replace(netloc="old.reddit.com", scheme="https"))


def resolve_share_url(url: str) -> str:
    attempt_url = _to_old_reddit(url)
    session = _make_session()
    try:
        resp = session.get(attempt_url, allow_redirects=True, timeout=15, stream=True)
        resp.close()
        final_url = _to_old_reddit(resp.url)
        if "/comments/" in final_url:
            logger.info("Resolved share link to: %s", final_url)
            return final_url
    except requests.RequestException as e:
        logger.warning("Could not resolve %s: %s", attempt_url, e)

    raise RuntimeError(f"Could not resolve share URL to a post URL: {url}")


def fetch_single_post(url: str) -> list[dict]:
    if "/s/" in urlparse(url).path:
        logger.info("Resolving Reddit share link: %s", url)
        url = resolve_share_url(url)
        logger.info("Resolved to: %s", url)

    parsed = urlparse(url)
    if "/comments/" not in parsed.path:
        raise ValueError(f"URL does not look like a Reddit post (missing /comments/): {url}")

    canonical = urlunparse(parsed._replace(netloc="old.reddit.com", scheme="https", query="", fragment=""))
    if not canonical.endswith(".json"):
        canonical = canonical.rstrip("/") + "/.json"

    logger.info("Fetching single post: %s", canonical)
    session = _make_session()
    data = _get(canonical, session)

    listing = data[0] if isinstance(data, list) else data
    children = listing.get("data", {}).get("children", [])
    posts = [c["data"] for c in children if c.get("kind") == "t3"]

    if not posts:
        raise RuntimeError(f"No post data found at {canonical}")

    logger.info("Fetched post: %r", posts[0].get("title", "")[:80])
    return posts


def _build_feed_url(subreddit: str, sort: str) -> str:
    if sort == "new":
        return f"{config.REDDIT_BASE_URL}/r/{subreddit}/new/.json"
    if sort == "top":
        return f"{config.REDDIT_BASE_URL}/r/{subreddit}/top/.json"
    return f"{config.REDDIT_BASE_URL}/r/{subreddit}/.json"


def _navigate_to_page(
    url: str,
    session: requests.Session,
    sort: str,
    timeframe: str,
    target_page: int,
) -> tuple[str | None, int]:
    """Traverse pages 1..target_page-1 to find the after cursor for page target_page.

    Returns (after_cursor, count_seen). Raises RuntimeError if the subreddit
    runs out of pages before target_page is reached.
    """
    if target_page == 1:
        return None, 0

    after: str | None = None
    count: int = 0
    for p in range(1, target_page):
        params: dict = {"limit": 25}
        if after:
            params["after"] = after
        if count:
            params["count"] = count
        if sort == "top":
            params["sort"] = "top"
            params["t"] = timeframe
        logger.info("Navigating to page %d/%d (after=%s)", p, target_page - 1, after)
        data = _get(url, session, params=params)
        children = data.get("data", {}).get("children", [])
        if not children:
            raise RuntimeError(
                f"Subreddit has fewer than {target_page} pages; stopped at page {p}"
            )
        after = data.get("data", {}).get("after")
        if not after:
            raise RuntimeError(
                f"Subreddit has fewer than {target_page} pages; no cursor after page {p}"
            )
        count += len(children)
    return after, count


def fetch_posts(
    subreddit: str,
    limit: int = 100,
    sort: str = "hot",
    timeframe: str = "day",
    page: int = 1,
) -> list[dict]:
    """Fetch up to `limit` posts from r/subreddit via forward pagination."""
    if page < 1:
        raise ValueError(f"page must be >= 1, got {page}")

    session = _make_session()
    url = _build_feed_url(subreddit, sort)
    posts: list[dict] = []
    after: str | None = None
    count: int = 0

    if page > 1:
        try:
            after, count = _navigate_to_page(url, session, sort, timeframe, page)
        except RuntimeError as e:
            logger.warning("Cannot reach page %d: %s", page, e)
            return []

    while len(posts) < limit:
        page_size = min(100, limit - len(posts))
        params: dict = {"limit": page_size}
        if after:
            params["after"] = after
        if count:
            params["count"] = count
        if sort == "top":
            params["sort"] = "top"
            params["t"] = timeframe

        logger.info(
            "Fetching r/%s [sort=%s page=%d] (after=%s, collected=%d/%d)",
            subreddit, sort, page, after, len(posts), limit,
        )
        try:
            data = _get(url, session, params=params)
        except RuntimeError as e:
            logger.error("Stopping pagination: %s", e)
            break

        children = data.get("data", {}).get("children", [])
        if not children:
            break

        for child in children:
            if child.get("kind") != "t3":
                continue
            posts.append(child["data"])

        count += len(children)
        after = data.get("data", {}).get("after")
        if not after:
            if len(posts) < limit:
                logger.info(
                    "Feed exhausted after %d posts (requested %d)",
                    len(posts), limit,
                )
            break

    logger.info("Fetched %d posts from r/%s", len(posts), subreddit)
    return posts
