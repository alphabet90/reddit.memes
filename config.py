import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

SUBREDDIT = os.getenv("SUBREDDIT", "argentina")
REPO_PATH = Path(os.getenv("REPO_PATH", str(Path(__file__).parent)))
TMP_DIR = REPO_PATH / "tmp"
STATE_FILE = REPO_PATH / "state.json"

REQUEST_DELAY = 1.0
MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024  # 10 MB

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".webp"}
IMAGE_HOSTS = {"i.redd.it", "i.imgur.com", "preview.redd.it", "external-preview.redd.it"}

REDDIT_BASE_URL = "https://old.reddit.com"
REDDIT_USER_AGENT = "MemeScraperBot/1.0 (educational project)"
