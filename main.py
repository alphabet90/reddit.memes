#!/usr/bin/env python3
import argparse
import logging
import sys
from pathlib import Path

import config


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Scrape Reddit, classify memes with Claude, and save them to the repo."
    )
    parser.add_argument("--subreddit", default=None, help="Subreddit to scrape (default: from config)")
    parser.add_argument("--limit", type=int, default=100, help="Max posts to scan (default: 100)")
    parser.add_argument("--batch-size", type=int, default=10, help="Images per git commit batch (default: 10)")
    parser.add_argument(
        "--classify-workers",
        type=int,
        default=None,
        metavar="N",
        help=f"Parallel classifier subprocesses (default: {config.CLASSIFY_WORKERS})",
    )
    parser.add_argument(
        "--classifier",
        default=config.CLASSIFIER,
        choices=["claude", "codex"],
        help=f"Vision classifier backend (default: {config.CLASSIFIER})",
    )
    parser.add_argument(
        "--locale",
        default="en",
        metavar="LOCALE",
        help=(
            "Prompt locale in ISO 639 / BCP 47 format (e.g. en, es-AR, es_AR). "
            "Loads prompts/prompt.{locale}.txt; falls back to the language root then en. "
            "(default: en)"
        ),
    )
    parser.add_argument("--repo-path", type=Path, default=None, help="Path to git repo (default: current dir)")
    parser.add_argument("--dry-run", action="store_true", help="Classify without saving or committing")
    parser.add_argument(
        "--no-branch",
        action="store_true",
        help="Skip auto branch creation and commit directly to the current branch",
    )
    parser.add_argument(
        "--reset-bloom",
        action="store_true",
        help="Delete the Bloom filter and reprocess every post from scratch",
    )
    parser.add_argument(
        "--restore-bloom",
        action="store_true",
        help="Rebuild the Bloom filter from existing memes in memes/ (uses .mdx sidecar files) and exit",
    )
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
        "--sort",
        default="hot",
        choices=["hot", "new", "top"],
        help="Feed sort order: hot (default), new, or top",
    )
    parser.add_argument(
        "--timeframe",
        default="day",
        choices=["hour", "day", "week", "month", "year", "all"],
        help="Time window for --sort=top (default: day). Ignored for hot/new.",
    )
    parser.add_argument(
        "--page",
        type=int,
        default=1,
        metavar="N",
        help=(
            "Page to start collecting from (default: 1). "
            "Pages 1..N-1 are traversed first to locate the cursor. "
            "Incompatible with --from-file and --post-url."
        ),
    )
    parser.add_argument(
        "--rebuild-content-index",
        action="store_true",
        help="Reindex SHA1 hashes of all existing memes (useful after manual additions to memes/)",
    )
    parser.add_argument(
        "--log-level",
        default="INFO",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="Log verbosity (default: INFO)",
    )
    args = parser.parse_args()

    if args.page != 1 and (args.from_file or args.post_url):
        parser.error("--page cannot be used with --from-file or --post-url")

    logging.basicConfig(
        level=args.log_level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    from src.pipeline import run
    from src.classifiers import ClaudeClassifier, CodexClassifier

    _backends = {"claude": ClaudeClassifier, "codex": CodexClassifier}
    classifier = _backends[args.classifier](locale=args.locale)

    if args.reset_bloom:
        removed = []
        for path in (config.BLOOM_FILTER_FILE, config.LEGACY_STATE_FILE):
            if path.exists():
                path.unlink()
                removed.append(path.name)
        if removed:
            logging.getLogger(__name__).info("State reset. Removed: %s", ", ".join(removed))

    if args.restore_bloom:
        from src.bloom_restore import restore_bloom_from_memes
        from src.post_tracker import PostTracker

        repo_path = args.repo_path or config.REPO_PATH
        tracker = PostTracker(config.BLOOM_FILTER_FILE)
        count = restore_bloom_from_memes(repo_path, tracker)
        logging.getLogger(__name__).info("Restored bloom filter from %d memes", count)
        return 0

    run(
        subreddit=args.subreddit or config.SUBREDDIT,
        limit=args.limit,
        repo_path=args.repo_path or config.REPO_PATH,
        batch_size=args.batch_size,
        dry_run=args.dry_run,
        from_file=args.from_file,
        post_url=args.post_url,
        min_comment_upvotes=args.min_comment_upvotes,
        sort=args.sort,
        timeframe=args.timeframe,
        page=args.page,
        rebuild_content_index=args.rebuild_content_index,
        classify_workers=args.classify_workers,
        classifier=classifier,
        create_branch=not args.no_branch,
        locale=args.locale,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
