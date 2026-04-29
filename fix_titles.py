#!/usr/bin/env python3
"""Generate new meme titles from descriptions using claude CLI and update .mdx files."""

import json
import re
import subprocess
import sys
from pathlib import Path

MEMES_DIR = Path("/home/user/reddit.memes/memes")
BATCH_SIZE = 50


def find_mdx_files() -> list[Path]:
    return sorted(MEMES_DIR.rglob("*.mdx"))


def parse_frontmatter(content: str) -> tuple[dict, str]:
    """Return (frontmatter_dict, body_after_frontmatter)."""
    if not content.startswith("---"):
        return {}, content
    end = content.index("---", 3)
    fm_text = content[3:end].strip()
    body = content[end + 3:]
    fm = {}
    for line in fm_text.splitlines():
        if ":" in line:
            key, _, val = line.partition(":")
            fm[key.strip()] = val.strip().strip('"')
    return fm, body


def extract_description(path: Path) -> str | None:
    text = path.read_text(encoding="utf-8")
    m = re.search(r'^description:\s*"(.+)"', text, re.MULTILINE)
    return m.group(1) if m else None


def generate_titles_batch(items: list[dict]) -> dict[str, str]:
    """Call claude CLI with a batch of {slug, description} items. Returns {slug: title}."""
    prompt_lines = [
        "You are a meme title generator. For each meme description below, write a short, punchy English title (5-10 words max) that captures the core joke or concept of the meme.",
        "Rules:",
        "- Title must be in English",
        "- Based ONLY on what is described (not the Reddit post title)",
        "- Capture the key label, punchline, or concept",
        "- Do NOT use quotes around the title",
        "- Return ONLY a JSON object mapping each slug to its new title, nothing else",
        "",
        "Descriptions:",
    ]
    for item in items:
        prompt_lines.append(f'  "{item["slug"]}": "{item["description"]}"')
    prompt_lines.append("")
    prompt_lines.append("Return format: {\"slug1\": \"Title 1\", \"slug2\": \"Title 2\", ...}")

    prompt = "\n".join(prompt_lines)

    result = subprocess.run(
        ["claude", "-p", prompt],
        capture_output=True,
        text=True,
        timeout=120,
    )
    if result.returncode != 0:
        print(f"claude error: {result.stderr[:200]}", file=sys.stderr)
        return {}

    output = result.stdout.strip()
    # Extract JSON from output
    json_match = re.search(r'\{.*\}', output, re.DOTALL)
    if not json_match:
        print(f"No JSON found in output: {output[:200]}", file=sys.stderr)
        return {}
    try:
        return json.loads(json_match.group())
    except json.JSONDecodeError as e:
        print(f"JSON parse error: {e}\nOutput: {output[:300]}", file=sys.stderr)
        return {}


def update_file(path: Path, new_title: str) -> bool:
    """Update title in frontmatter and # heading in body."""
    content = path.read_text(encoding="utf-8")

    # Update frontmatter title
    new_content = re.sub(
        r'^(title:\s*)".+"',
        f'title: "{new_title}"',
        content,
        count=1,
        flags=re.MULTILINE,
    )

    # Update the # heading line (first # heading after frontmatter)
    # Find end of frontmatter
    fm_end = new_content.index("---", 3) + 3
    before = new_content[:fm_end]
    after = new_content[fm_end:]
    after = re.sub(r'^# .+', f'# {new_title}', after, count=1, flags=re.MULTILINE)
    new_content = before + after

    if new_content == content:
        return False
    path.write_text(new_content, encoding="utf-8")
    return True


def main():
    files = find_mdx_files()
    print(f"Found {len(files)} .mdx files")

    # Collect descriptions
    items = []
    skipped = 0
    for path in files:
        desc = extract_description(path)
        if desc:
            items.append({"slug": path.stem, "path": str(path), "description": desc})
        else:
            skipped += 1

    print(f"Extracted {len(items)} descriptions ({skipped} skipped - no description)")

    # Process in batches
    all_titles: dict[str, str] = {}
    for i in range(0, len(items), BATCH_SIZE):
        batch = items[i : i + BATCH_SIZE]
        print(f"Processing batch {i // BATCH_SIZE + 1}/{(len(items) + BATCH_SIZE - 1) // BATCH_SIZE} ({len(batch)} items)...")
        titles = generate_titles_batch(batch)
        all_titles.update(titles)
        print(f"  Got {len(titles)} titles")

    print(f"\nTotal titles generated: {len(all_titles)}")

    # Update files
    updated = 0
    failed = []
    slug_to_path = {item["slug"]: Path(item["path"]) for item in items}

    for slug, title in all_titles.items():
        path = slug_to_path.get(slug)
        if path is None:
            continue
        if update_file(path, title):
            updated += 1
        else:
            failed.append(slug)

    print(f"Updated {updated} files")
    if failed:
        print(f"Failed to update {len(failed)} files: {failed[:5]}")

    missing = [item["slug"] for item in items if item["slug"] not in all_titles]
    if missing:
        print(f"No title generated for {len(missing)} files: {missing[:5]}")


if __name__ == "__main__":
    main()
