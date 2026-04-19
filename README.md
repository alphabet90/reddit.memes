# reddit.memes

A pipeline that scrapes Reddit posts, classifies images as memes using Claude's vision capabilities, and saves confirmed memes into a git-tracked folder organized by category.

## How it works

1. **Scrape** — fetches posts from `old.reddit.com` with pagination and exponential backoff, extracting image URLs from direct links, previews, and galleries.
2. **Download** — saves images to `tmp/` using SHA1-based filenames to deduplicate. Enforces a 10 MB size limit and content-type validation.
3. **Classify** — spawns a `claude` subprocess per image with vision enabled. Returns a category or marks the image as not a meme.
4. **Save & commit** — copies confirmed memes to `memes/{category}/{slug}{ext}` and creates a git commit per batch.

Processed post IDs and image URLs are tracked in a **Bloom filter** (`processed.bloom`, ~360 KB fixed size) so interrupted runs resume without reprocessing. See `src/bloom.py` for the reusable implementation.

## Requirements

- Python 3.10+
- `claude` CLI in PATH
- `git` in PATH

## Setup

```bash
pip install -r requirements.txt
cp .env.example .env
# Edit .env to set SUBREDDIT and REPO_PATH
```

## Usage

```bash
# Basic run
python main.py --subreddit argentina --limit 100

# Key flags
--batch-size 10        # Images per git commit (default: 10)
--dry-run              # Classify without saving or committing
--reset-bloom          # Delete the Bloom filter and reprocess everything
--from-file posts.json # Load posts from saved Reddit JSON instead of fetching live
--post-url URL         # Scrape a single Reddit post by URL
--log-level DEBUG      # Increase verbosity
```

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `SUBREDDIT` | `argentina` | Subreddit to scrape |
| `REPO_PATH` | current directory | Path to the git repository where memes are saved |

Whitelisted image hosts are defined in `config.py` (`IMAGE_HOSTS`). Add new sources there as needed.

## Output structure

```
memes/
  simpsons/
    some-meme-slug.jpg
  pepe/
    another-meme.png
tmp/           # temporary downloads, not committed
processed.bloom # Bloom filter of processed post IDs + URLs (not committed)
```
