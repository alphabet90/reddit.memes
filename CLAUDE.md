# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Reddit meme scraper pipeline that fetches posts from a subreddit, downloads images, classifies them as memes using the Claude Code CLI (via subprocess with vision), and saves confirmed memes into a git-tracked folder organized by category.

## Running the Application

```bash
# Basic run
python main.py --subreddit argentina --limit 100

# Key flags
--batch-size 10        # Images per git commit (default: 10)
--dry-run              # Classify without saving or committing
--reset-bloom          # Delete processed.bloom and reprocess everything
--from-file posts.json # Load posts from saved Reddit JSON instead of fetching live
--log-level DEBUG      # Increase verbosity
```

**Prerequisites**: `claude` CLI and `git` must be in PATH. Python 3.10+ required.

**Dependencies**: `pip install -r requirements.txt` (only `requests` and `python-dotenv`).

**Config**: Copy `.env.example` to `.env` and set `SUBREDDIT` and `REPO_PATH` as needed.

## Architecture

The pipeline is orchestrated by `src/pipeline.py` and flows through four stages:

1. **Scrape** (`src/scraper.py`) — Fetches posts from `old.reddit.com` JSON API with pagination and exponential backoff. Extracts image URLs from three possible locations per post: direct URL, `preview.images[]`, and `media_metadata[]` (galleries).

2. **Download** (`src/downloader.py`) — Downloads images to `tmp/` using SHA1-based filenames for deduplication. Enforces a 10 MB size limit and content-type validation. Skips URLs already recorded in the Bloom filter.

3. **Classify** (`src/classifier.py`) — Spawns a `claude` subprocess per image with `--tools Read` (enabling vision) and `--dangerously-skip-permissions`. Parses JSON from stdout. Retries up to 2 times with a 120s timeout. Returns a `ClassificationResult` dataclass.

4. **Save & Commit** (`src/saver.py`) — Copies confirmed memes to `memes/{category}/{slug}{ext}`, runs `git add` + `git commit` per batch. Commit messages summarize the batch (e.g., `Add 5 memes from r/argentina batch 1 [simpsons(2), pepe(3)]`).

**State management**: `src/post_tracker.py::PostTracker` persists processed post IDs and image URLs in a Bloom filter file (`processed.bloom`) for O(1) membership tests. The underlying reusable filter lives in `src/bloom.py`. On first run after upgrading, a legacy `state.json` is automatically migrated into the Bloom filter and removed.

## Key Conventions

- **Python 3.10+ union syntax** is used throughout (`Path | None`, not `Optional[Path]`).
- **No async** — the pipeline is intentionally synchronous.
- **Image hosts are whitelisted** in `config.py` (`IMAGE_HOSTS`). New sources must be added there.
- **Slug sanitization** (`saver.py::_sanitize_slug`) keeps only alphanumerics and hyphens, max 80 chars. Filename collisions are resolved by appending `-2`, `-3`, etc.
- **Claude classification prompt** lives in `classifier.py::_PROMPT` as a module-level constant. Edit it there to change classification behavior or output schema.
- All git operations are raw `subprocess` calls — no `gitpython` or similar library.
