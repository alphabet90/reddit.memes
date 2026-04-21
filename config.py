from pathlib import Path

SUBREDDIT = "argentina"
REPO_PATH = Path.cwd()
TMP_DIR = REPO_PATH / "tmp"
BLOOM_FILTER_FILE = REPO_PATH / "processed.bloom"
LEGACY_STATE_FILE = REPO_PATH / "state.json"
BLOOM_CAPACITY = 200_000
BLOOM_ERROR_RATE = 1e-3

REQUEST_DELAY = 1.0
MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024  # 10 MB
MIN_COMMENT_UPVOTES = 0

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png"}
IMAGE_HOSTS = {"i.redd.it", "i.imgur.com", "preview.redd.it", "external-preview.redd.it"}

REDDIT_BASE_URL = "https://old.reddit.com"
REDDIT_USER_AGENT = "MemeScraperBot/1.0 (educational project)"

CLASSIFY_WORKERS: int = 4   # concurrent Claude subprocesses
DOWNLOAD_WORKERS: int = 8   # concurrent image downloads

