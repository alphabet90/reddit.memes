import logging
import shutil
import subprocess
from pathlib import Path

from src.classifier import ClassificationResult

logger = logging.getLogger(__name__)


def _sanitize_slug(slug: str) -> str:
    import re
    slug = slug.lower().strip()
    slug = re.sub(r"[^a-z0-9\-]", "-", slug)
    slug = re.sub(r"-{2,}", "-", slug).strip("-")
    return slug[:80] or "meme"


def _unique_dest(base: Path) -> Path:
    if not base.exists():
        return base
    stem, suffix = base.stem, base.suffix
    for i in range(2, 100):
        candidate = base.with_name(f"{stem}-{i}{suffix}")
        if not candidate.exists():
            return candidate
    return base.with_name(f"{stem}-{hash(str(base)) % 10000}{suffix}")


def save_meme(image_path: Path, result: ClassificationResult, repo_path: Path) -> Path | None:
    category = _sanitize_slug(result.category) if result.category else "other"
    slug = _sanitize_slug(result.filename_slug) if result.filename_slug else "meme"
    ext = image_path.suffix.lower() or ".jpg"

    dest_dir = repo_path / "memes" / category
    dest_dir.mkdir(parents=True, exist_ok=True)

    dest = _unique_dest(dest_dir / f"{slug}{ext}")
    shutil.copy2(image_path, dest)
    logger.info("Saved meme → %s", dest.relative_to(repo_path))
    return dest


def git_add(repo_path: Path, paths: list[Path]) -> bool:
    try:
        subprocess.run(
            ["git", "add"] + [str(p) for p in paths],
            cwd=repo_path,
            check=True,
            capture_output=True,
            text=True,
        )
        return True
    except subprocess.CalledProcessError as e:
        logger.error("git add failed: %s", e.stderr)
        return False


def git_commit(repo_path: Path, message: str) -> bool:
    try:
        result = subprocess.run(
            ["git", "commit", "-m", message],
            cwd=repo_path,
            check=True,
            capture_output=True,
            text=True,
        )
        logger.info("Committed: %s", result.stdout.strip().splitlines()[0] if result.stdout else message)
        return True
    except subprocess.CalledProcessError as e:
        if "nothing to commit" in e.stdout + e.stderr:
            logger.debug("Nothing to commit")
            return True
        logger.error("git commit failed: %s", e.stderr)
        return False


def save_and_commit_batch(
    items: list[tuple[ClassificationResult, Path]],
    repo_path: Path,
    batch_num: int,
    subreddit: str,
) -> list[Path]:
    saved = []
    for result, tmp_path in items:
        path = save_meme(tmp_path, result, repo_path)
        if path:
            saved.append(path)

    if not saved:
        return []

    if git_add(repo_path, saved):
        category_counts: dict[str, int] = {}
        for r, _ in items:
            if r.is_meme:
                cat = _sanitize_slug(r.category) if r.category else "other"
                category_counts[cat] = category_counts.get(cat, 0) + 1

        summary = ", ".join(f"{cat}({n})" for cat, n in sorted(category_counts.items()))
        msg = f"Add {len(saved)} memes from r/{subreddit} batch {batch_num} [{summary}]"
        git_commit(repo_path, msg)

    return saved
