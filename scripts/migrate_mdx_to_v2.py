#!/usr/bin/env python3
"""One-shot rewrite of MDX frontmatter from V1 to V2 format.

V1 frontmatter (flat):

    ---
    category: argentina-football
    slug: cat-world-cup
    title: Cat at the World Cup
    description: A cat in a jersey
    image: ./cat-world-cup.jpg
    tags: [argentina, football]
    ---

V2 frontmatter (multilingual + multi-image):

    ---
    category: argentina-football
    slug: cat-world-cup
    default_locale: en
    images:
      - path: ./cat-world-cup.jpg
        is_primary: true
    tags: [argentina, football]
    translations:
      en:
        title: Cat at the World Cup
        description: A cat in a jersey
    ---

The script is idempotent: files that already have a `translations` block are
skipped. Run before the first POST /admin/reindex against the V2 schema.

Usage:
    python scripts/migrate_mdx_to_v2.py memes/

The script edits files in place. Pass --dry-run to print the planned
changes without writing.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

import yaml


FRONTMATTER_DELIMITER = "---"
DEFAULT_LOCALE = "en"


def parse_mdx(path: Path) -> tuple[dict[str, Any], str] | None:
    """Return (frontmatter, body) or None if the file has no frontmatter."""
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    if not lines or lines[0].strip() != FRONTMATTER_DELIMITER:
        return None
    end = -1
    for i in range(1, len(lines)):
        if lines[i].strip() == FRONTMATTER_DELIMITER:
            end = i
            break
    if end < 0:
        return None
    yaml_block = "".join(lines[1:end])
    fm = yaml.safe_load(yaml_block) or {}
    body = "".join(lines[end + 1 :])
    return fm, body


def transform(fm: dict[str, Any]) -> dict[str, Any] | None:
    """Convert a V1 frontmatter dict into V2 shape. Returns None if already V2."""
    if "translations" in fm:
        return None

    title = fm.get("title", "") or ""
    description = fm.get("description")
    image = fm.get("image")

    new: dict[str, Any] = {}
    # Preserve canonical ordering for stable diffs.
    for key in (
        "category",
        "slug",
        "subreddit",
        "author",
        "score",
        "created_at",
        "source_url",
        "post_url",
    ):
        if key in fm:
            new[key] = fm[key]

    new["default_locale"] = fm.get("default_locale", DEFAULT_LOCALE)

    if image:
        new["images"] = [{"path": image, "is_primary": True}]
    elif "images" in fm:
        new["images"] = fm["images"]

    if "tags" in fm:
        new["tags"] = fm["tags"]

    translation: dict[str, Any] = {"title": title}
    if description is not None:
        translation["description"] = description
    new["translations"] = {new["default_locale"]: translation}
    return new


def write_mdx(path: Path, fm: dict[str, Any], body: str) -> str:
    fm_yaml = yaml.safe_dump(
        fm, sort_keys=False, allow_unicode=True, default_flow_style=False
    )
    return f"{FRONTMATTER_DELIMITER}\n{fm_yaml}{FRONTMATTER_DELIMITER}\n{body}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, help="memes/ directory to walk")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not args.root.is_dir():
        print(f"Not a directory: {args.root}", file=sys.stderr)
        return 1

    converted = skipped = unchanged = 0
    for mdx in sorted(args.root.rglob("*.mdx")):
        parsed = parse_mdx(mdx)
        if parsed is None:
            skipped += 1
            continue
        fm, body = parsed
        new_fm = transform(fm)
        if new_fm is None:
            unchanged += 1
            continue
        rendered = write_mdx(mdx, new_fm, body)
        if args.dry_run:
            print(f"--- WOULD REWRITE: {mdx} ---")
            print(rendered)
        else:
            mdx.write_text(rendered, encoding="utf-8")
        converted += 1

    print(
        f"Done. converted={converted} unchanged={unchanged} skipped={skipped}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
