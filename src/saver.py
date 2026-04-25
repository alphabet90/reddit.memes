import datetime
import logging
import shutil
import subprocess
from pathlib import Path

from src.classifier import ClassificationResult
from src.models import PostMetadata

logger = logging.getLogger(__name__)


def _yaml_str(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"')


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


def write_mdx(
    image_dest: Path,
    result: ClassificationResult,
    meta: PostMetadata,
    repo_path: Path,
) -> Path | None:
    mdx_path = image_dest.with_suffix(".mdx")
    image_rel = str(image_dest.relative_to(repo_path)).replace("\\", "/")
    created_at = datetime.datetime.utcfromtimestamp(meta.created_utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    post_url = f"https://reddit.com{meta.permalink}"
    tags = f'"{_yaml_str(meta.subreddit)}", "{_yaml_str(result.category)}"'
    slug = _sanitize_slug(result.filename_slug) if result.filename_slug else "meme"
    title = meta.title or slug

    content = (
        f'---\n'
        f'title: "{_yaml_str(title)}"\n'
        f'description: "{_yaml_str(result.description)}"\n'
        f'author: "{_yaml_str(meta.author)}"\n'
        f'subreddit: "{_yaml_str(meta.subreddit)}"\n'
        f'category: "{_yaml_str(result.category)}"\n'
        f'slug: "{slug}"\n'
        f'score: {meta.score}\n'
        f'created_at: "{created_at}"\n'
        f'source_url: "{_yaml_str(result.url)}"\n'
        f'post_url: "{post_url}"\n'
        f'image: "./{image_dest.name}"\n'
        f'tags: [{tags}]\n'
        f'---\n'
        f'\n'
        f'# {title}\n'
        f'\n'
        f'{result.description}\n'
        f'\n'
        f'**Category**: {result.category} | **Author**: u/{meta.author} | **Score**: {meta.score} upvotes\n'
        f'\n'
        f'[View original post on Reddit]({post_url})\n'
    )
    try:
        mdx_path.write_text(content, encoding="utf-8")
        logger.info("Wrote MDX → %s", mdx_path.relative_to(repo_path))
        return mdx_path
    except OSError as e:
        logger.warning("Failed to write MDX for %s: %s", image_dest.name, e)
        return None


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


def git_create_branch(repo_path: Path, branch_name: str) -> bool:
    try:
        subprocess.run(
            ["git", "checkout", "-b", branch_name],
            cwd=repo_path,
            check=True,
            capture_output=True,
            text=True,
        )
        logger.info("Created and checked out branch: %s", branch_name)
        return True
    except subprocess.CalledProcessError as e:
        logger.error("git checkout -b failed: %s", e.stderr)
        return False


def save_and_commit_batch(
    items: list[tuple[ClassificationResult, Path]],
    repo_path: Path,
    batch_num: int,
    subreddit: str,
    url_to_meta: dict[str, PostMetadata] | None = None,
) -> list[Path]:
    saved_images: list[Path] = []
    saved_mdx: list[Path] = []
    for result, tmp_path in items:
        path = save_meme(tmp_path, result, repo_path)
        if path:
            saved_images.append(path)
            meta = (url_to_meta or {}).get(result.url)
            if meta is not None:
                mdx = write_mdx(path, result, meta, repo_path)
                if mdx:
                    saved_mdx.append(mdx)

    saved = saved_images
    if not saved:
        return []

    if git_add(repo_path, saved_images + saved_mdx):
        category_counts: dict[str, int] = {}
        for r, _ in items:
            if r.is_meme:
                cat = _sanitize_slug(r.category) if r.category else "other"
                category_counts[cat] = category_counts.get(cat, 0) + 1

        summary = ", ".join(f"{cat}({n})" for cat, n in sorted(category_counts.items()))
        msg = f"Add {len(saved)} memes from r/{subreddit} batch {batch_num} [{summary}]"
        git_commit(repo_path, msg)

    return saved
