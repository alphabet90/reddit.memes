from pathlib import Path

SUBREDDIT = "argentina"
REPO_PATH = Path.cwd()
TMP_DIR = REPO_PATH / "tmp"
STATE_FILE = REPO_PATH / "state.json"

REQUEST_DELAY = 1.0
MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024  # 10 MB
MIN_COMMENT_UPVOTES = 10

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".webp"}
IMAGE_HOSTS = {"i.redd.it", "i.imgur.com", "preview.redd.it", "external-preview.redd.it"}

REDDIT_BASE_URL = "https://old.reddit.com"
REDDIT_USER_AGENT = "MemeScraperBot/1.0 (educational project)"
