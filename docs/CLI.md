# CLI Reference

Reddit meme scraper — fetch posts from Reddit, classify them with Claude, and save confirmed memes to a git-tracked folder.

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
| `--min-comment-upvotes N` | `0` | Also scrape images from comments with at least N upvotes |
| `--dry-run` | off | Classify without saving files or creating git commits |

### State

| Flag | Description |
|---|---|
| `--reset-bloom` | Delete the Bloom filter and reprocess all posts from scratch |

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

## Exit Codes

| Code | Meaning |
|---|---|
| `0` | Success (even if zero memes were found) |
| `2` | Argument error (printed by argparse — e.g. incompatible flags) |
| `1` | Runtime error (unhandled exception) |
