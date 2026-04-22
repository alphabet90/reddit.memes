# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Reddit meme scraper pipeline that fetches posts from a subreddit, downloads images, classifies them as memes using a pluggable vision classifier (Claude Code CLI by default, or Codex CLI), and saves confirmed memes into a git-tracked folder organized by category.

## Running the Application

```bash
# Basic run
python main.py --subreddit argentina --limit 100

# Key flags
--batch-size 10                   # Images per git commit (default: 10)
--dry-run                         # Classify without saving or committing
--reset-bloom                     # Delete processed.bloom and reprocess everything
--from-file posts.json            # Load posts from saved Reddit JSON instead of fetching live
--post-url <url>                  # Fetch and process a single Reddit post by URL
--min-comment-upvotes 10          # Minimum upvotes for comment images to be included
--classifier codex                # Use Codex CLI instead of Claude (default: claude)
--log-level DEBUG                 # Increase verbosity
```

**Prerequisites**: `claude` CLI (or `codex` CLI for `--classifier codex`) and `git` must be in PATH. Python 3.10+ required.

**Dependencies**: `pip install -r requirements.txt` (only `requests` and `python-dotenv`). Install `pytest` separately for tests.

**Config**: Copy `.env.example` to `.env` and set `SUBREDDIT` and `REPO_PATH` as needed.

## Testing

```bash
# Run all tests
pytest tests/

# Run a single test file
pytest tests/test_bloom.py

# Run a single test by name
pytest tests/test_bloom.py::test_add_and_contains
```

Tests cover `BloomFilter` (`tests/test_bloom.py`, 13 tests) and `PostTracker` (`tests/test_post_tracker.py`, 8 tests). No tests exist yet for the pipeline stages (scraper, downloader, classifier, saver).

## Architecture

The pipeline is orchestrated by `src/pipeline.py` and flows through four stages:

1. **Scrape** (`src/scraper.py`) — Fetches posts from `old.reddit.com` JSON API with pagination and exponential backoff. Extracts image URLs from three locations per post: direct URL, `preview.images[]`, and `media_metadata[]` (galleries). Pagination stops early after 3 consecutive already-seen posts (`EARLY_STOP_CONSECUTIVE_HITS` in `config.py`). Also supports `fetch_single_post()` for a URL and `fetch_comment_images()` for images in post comments filtered by upvote count.

2. **Download** (`src/downloader.py`) — Downloads images to `tmp/` using SHA1-based filenames for deduplication. Enforces a 10 MB size limit and content-type validation. Skips URLs already recorded in the Bloom filter.

3. **Classify** (`src/classifiers/`) — A `BaseClassifier` ABC (`src/classifiers/base.py`) defines `classify_image(path, url)` as the single abstract method; `classify_batch()` is a concrete default using `ThreadPoolExecutor`. `ClassificationResult` dataclass lives in `base.py`. `ClaudeClassifier` (default) spawns a `claude` subprocess with `--tools Read` and `--dangerously-skip-permissions`. `CodexClassifier` spawns a `codex` subprocess with `--image`. Both retry up to 2 times with a 120s timeout. Select the backend with `--classifier claude|codex` (config default: `CLASSIFIER = "claude"`).

4. **Save & Commit** (`src/saver.py`) — Copies confirmed memes to `memes/{category}/{slug}{ext}`, runs `git add` + `git commit` per batch. Commit messages summarize the batch (e.g., `Add 5 memes from r/argentina batch 1 [simpsons(2), pepe(3)]`).

**State management**: `src/post_tracker.py::PostTracker` persists processed post IDs and image URLs in a Bloom filter file (`processed.bloom`) for O(1) membership tests. The underlying reusable filter lives in `src/bloom.py` (uses Kirsch–Mitzenmacher double-hashing, atomic writes via `.tmp` files, stdlib-only). On first run after upgrading, a legacy `state.json` is automatically migrated into the Bloom filter and removed.

## Key Conventions

- **Python 3.10+ union syntax** is used throughout (`Path | None`, not `Optional[Path]`).
- **No async** — the pipeline is intentionally synchronous.
- **Image hosts are whitelisted** in `config.py` (`IMAGE_HOSTS`). New sources must be added there.
- **Slug sanitization** (`saver.py::_sanitize_slug`) keeps only alphanumerics and hyphens, max 80 chars. Filename collisions are resolved by appending `-2`, `-3`, etc.
- **Classifier prompts** live as module-level `_PROMPT` constants in each backend (`src/classifiers/claude_classifier.py`, `src/classifiers/codex_classifier.py`). Edit the relevant file to change classification behavior or output schema.
- **Adding a new classifier**: subclass `BaseClassifier` from `src/classifiers/base.py`, implement `classify_image`, register it in `src/classifiers/__init__.py` and the `_backends` dict in `main.py`.
- All git operations are raw `subprocess` calls — no `gitpython` or similar library.
