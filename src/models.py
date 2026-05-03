from dataclasses import dataclass


@dataclass
class PostMetadata:
    post_id: str        # post["name"] e.g. "t3_abc123"
    title: str          # post["title"]
    author: str         # post["author"]
    subreddit: str      # post["subreddit"]
    score: int          # upvotes: comment["ups"] for comment images, post["ups"] for post images
    created_utc: float  # post["created_utc"] — Unix timestamp
    permalink: str      # post["permalink"] e.g. "/r/argentina/comments/…"
