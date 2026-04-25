# CLI Reference

Reddit meme scraper — fetch posts from Reddit, classify them with a vision classifier (Claude or Codex), and save confirmed memes to a git-tracked folder.

## Synopsis

```
python main.py [OPTIONS]
```

## Quick Examples

```bash
# Scrape hot feed, classify up to 100 posts (default)
python main.py --subreddit argentina

# Fetch the newest 50 posts
python main.py --subreddit argentina --sort new --limit 50

# Top posts of the week, classify without saving
python main.py --subreddit argentina --sort top --timeframe week --dry-run

# Top of all time, start from page 4
python main.py --subreddit argentina --sort top --timeframe all --page 4

# Hot feed, start from page 3 (skips first 50 posts)
python main.py --subreddit argentina --page 3 --limit 50

# Process a single post by URL
python main.py --post-url https://old.reddit.com/r/argentina/comments/abc123/title/

# Reprocess everything from scratch (clear Bloom filter)
python main.py --subreddit argentina --reset-bloom --dry-run
```

---

## Options

### Feed Selection

| Flag | Default | Description |
|---|---|---|
| `--subreddit NAME` | from `.env` | Subreddit to scrape (e.g. `argentina`) |
| `--limit N` | `100` | Maximum number of posts to scan |
| `--sort {hot,new,top}` | `hot` | Feed sort order (see [Sort Modes](#sort-modes)) |
| `--timeframe {hour,day,week,month,year,all}` | `day` | Time window for `--sort=top` (ignored for hot/new) |
| `--page N` | `1` | Page to start collecting from (see [Page Navigation](#page-navigation)) |

### Processing

| Flag | Default | Description |
|---|---|---|
| `--batch-size N` | `10` | Number of images per git commit batch |
| `--classifier {claude,codex}` | `claude` | Vision classifier backend (see [Classifiers](#classifiers)) |
| `--classify-workers N` | `4` | Number of parallel classifier subprocesses |
| `--min-comment-upvotes N` | `0` | Also scrape images from comments with at least N upvotes |
| `--dry-run` | off | Classify without saving files or creating git commits |

### State

| Flag | Description |
|---|---|
| `--reset-bloom` | Delete the Bloom filter and reprocess all posts from scratch |
| `--restore-bloom` | Rebuild the Bloom filter from saved memes in `memes/` and exit (see [Restoring the Bloom Filter](#restoring-the-bloom-filter)) |

### Source Override

These flags are mutually exclusive with each other, and incompatible with `--page`.

| Flag | Description |
|---|---|
| `--from-file JSON_FILE` | Load posts from a saved Reddit JSON file instead of fetching live |
| `--post-url URL` | Fetch and process a single Reddit post by URL (supports share links) |

### Output

| Flag | Default | Description |
|---|---|---|
| `--repo-path PATH` | current directory | Path to the git repository where memes are saved |
| `--log-level {DEBUG,INFO,WARNING,ERROR}` | `INFO` | Log verbosity |

---

## Classifiers

The `--classifier` flag selects which vision-capable CLI tool performs meme classification. Both backends use the same JSON output schema and retry logic (2 attempts, 120 s timeout each).

### `claude` (default)

Spawns the [Claude Code CLI](https://claude.ai/code) via subprocess with `--tools Read` (enables reading the image file) and `--dangerously-skip-permissions`.

```bash
python main.py --classifier claude
```

**Prerequisites**: `claude` must be in PATH and authenticated.

### `codex`

Spawns the [OpenAI Codex CLI](https://developers.openai.com/codex/cli/features#image-inputs) via subprocess, passing the image with `--image`.

```bash
python main.py --classifier codex
```

**Prerequisites**: `codex` must be in PATH and authenticated.

### Adding a custom classifier

Subclass `BaseClassifier` from `src/classifiers/base.py`, implement `classify_image(image_path, url) -> ClassificationResult`, register it in `src/classifiers/__init__.py` and add it to the `_backends` dict in `main.py`.

---

## Sort Modes

### `hot` (default)

Fetches the current hot feed — posts ranked by Reddit's score and recency algorithm.

```
GET https://old.reddit.com/r/{subreddit}/.json
```

### `new`

Fetches posts in strict reverse-chronological order (newest first).

```
GET https://old.reddit.com/r/{subreddit}/new/.json
```

### `top`

Fetches top-scoring posts within a time window set by `--timeframe`.

```
GET https://old.reddit.com/r/{subreddit}/top/.json?sort=top&t={timeframe}
```

#### Timeframe values

| `--timeframe` | Window |
|---|---|
| `hour` | Past hour |
| `day` | Past 24 hours *(default)* |
| `week` | Past week |
| `month` | Past month |
| `year` | Past year |
| `all` | All time |

> `--timeframe` is silently ignored when `--sort` is `hot` or `new`.

---

## Page Navigation

Reddit paginates its feeds in chunks of 25 posts. `--page N` tells the scraper to skip to page N before starting to collect posts.

**How it works:**

- Pages 1 through N-1 are traversed in sequence to obtain Reddit's pagination cursor (`after`).
- Each traversal page costs one HTTP request (plus the standard `REQUEST_DELAY`).
- Collection begins at page N and continues until `--limit` posts are gathered (which may span multiple pages beyond N).

**Example — page costs:**

| `--page` | Extra traversal requests |
|---|---|
| 1 (default) | 0 |
| 2 | 1 |
| 4 | 3 |
| 10 | 9 |

**Warnings:**

- If the subreddit has fewer pages than requested, the scraper logs a warning and exits with 0 memes saved.
- `--page` cannot be combined with `--from-file` or `--post-url` (exits with code 2).

---

## Configuration (`.env`)

Copy `.env.example` to `.env` and set these variables to avoid passing them on every run:

```dotenv
SUBREDDIT=argentina
REPO_PATH=/path/to/your/repo
```

CLI flags always override `.env` values.

---

## Restoring the Bloom Filter

If `processed.bloom` is lost or corrupted the pipeline "forgets" all prior work and would re-download and re-classify every image on the next run. `--restore-bloom` reconstructs the filter from the memes that are already saved on disk without re-running the pipeline.

```bash
python main.py --restore-bloom
```

**What it recovers:**

- Image URLs (`source_url` from each `.mdx` sidecar file)
- Reddit post IDs (derived from `post_url` in each `.mdx`)
- SHA1 content hashes (computed directly from each image file)

**What it cannot recover:**

- Images that were downloaded and classified as *not* a meme — those were never saved, so the pipeline may re-classify them on the next run. They will be rejected again without being saved, so there is no harm.

**Typical use cases:**

- Bloom filter file accidentally deleted or corrupted
- Restoring state after cloning the repo onto a new machine (where only the `memes/` folder was synced via git)
- Recovering from a failed `--reset-bloom` run

**Combining with `--reset-bloom`:**

Passing both flags first deletes the existing filter, then immediately rebuilds it from saved memes:

```bash
python main.py --reset-bloom --restore-bloom
```

---

## Exit Codes

| Code | Meaning |
|---|---|
| `0` | Success (even if zero memes were found) |
| `2` | Argument error (printed by argparse — e.g. incompatible flags) |
| `1` | Runtime error (unhandled exception) |
