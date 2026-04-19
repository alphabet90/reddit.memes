#!/usr/bin/env python3
import argparse
import logging
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Scrape Reddit, classify memes with Claude, and save them to the repo."
    )
    parser.add_argument("--subreddit", default=None, help="Subreddit to scrape (default: from config)")
    parser.add_argument("--limit", type=int, default=100, help="Max posts to scan (default: 100)")
    parser.add_argument("--batch-size", type=int, default=10, help="Images per git commit batch (default: 10)")
    parser.add_argument("--repo-path", type=Path, default=None, help="Path to git repo (default: current dir)")
    parser.add_argument("--dry-run", action="store_true", help="Classify without saving or committing")
    parser.add_argument("--reset-state", action="store_true", help="Clear state.json and reprocess everything")
    source_group = parser.add_mutually_exclusive_group()
    source_group.add_argument(
        "--from-file",
        type=Path,
        default=None,
        metavar="JSON_FILE",
        help="Load posts from a saved Reddit JSON file instead of fetching from Reddit (useful for testing)",
    )
    source_group.add_argument(
        "--post-url",
        default=None,
        metavar="URL",
        help="Scrape a single Reddit post by URL (supports share links like reddit.com/r/x/s/XXXX)",
    )
    parser.add_argument(
        "--min-comment-upvotes",
        type=int,
        default=config.MIN_COMMENT_UPVOTES,
        help="Also scrape images from comments with at least this many upvotes. Set to 0 to disable (default: 10).",
    )
    parser.add_argument(
        "--log-level",
        default="INFO",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="Log verbosity (default: INFO)",
    )
    args = parser.parse_args()

    logging.basicConfig(
        level=args.log_level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    import config
    from src.pipeline import run

    if args.reset_state and config.STATE_FILE.exists():
        config.STATE_FILE.unlink()
        logging.getLogger(__name__).info("State reset.")

    run(
        subreddit=args.subreddit or config.SUBREDDIT,
        limit=args.limit,
        repo_path=args.repo_path or config.REPO_PATH,
        batch_size=args.batch_size,
        dry_run=args.dry_run,
        from_file=args.from_file,
        post_url=args.post_url,
        min_comment_upvotes=args.min_comment_upvotes,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
