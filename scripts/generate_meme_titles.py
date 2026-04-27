#!/usr/bin/env python3
"""Generate canonical meme titles from description and slug.

The title field currently holds the Reddit post title, which is wrong.
This script derives the actual meme name from the description + slug:

  1. If the description contains a quoted text caption (e.g. "with Spanish text
     saying 'Demasiado cine'"), that caption becomes the title — it's the phrase
     that defines the meme.
  2. Otherwise the slug is converted to Title Case to produce a clean template
     name (e.g. "flik-ant-leaving-with-bindle" → "Flik Ant Leaving with Bindle").

Locale handling:
  - "es" locale  → extract Spanish caption first, then slug Title Case
  - "en" locale  → slug Title Case (canonical English template name)
  - "intl" (both en+es) → title_en from slug, title_es from caption or slug

Idempotent: re-running produces identical output.

Usage:
    python scripts/generate_meme_titles.py memes/
    python scripts/generate_meme_titles.py memes/ --dry-run
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

import yaml

FRONTMATTER_DELIMITER = "---"

# Matches quoted text after "with [Spanish] text/caption [saying|asking|...]"
# Captures content inside single or double quotes.
_CAPTION_RE = re.compile(
    r"(?:with (?:Spanish |the )?(?:text|caption)|captioned(?: with)?|"
    r"overlaid text|text overlay|text reading|text that (?:says?|reads?))"
    r"(?:\s+(?:saying|asking|that says?|reading|showing))?"
    r"\s*['‘’“”](.+?)['‘’“”]",
    re.IGNORECASE,
)

# Articles/prepositions kept lowercase in English title case (when not first word).
_LOWER_EN = frozenset(
    "a an the in on at of to for and or but with from by as".split()
)


def _slug_to_title(slug: str) -> str:
    words = slug.replace("-", " ").split()
    return " ".join(
        w.capitalize() if i == 0 or w.lower() not in _LOWER_EN else w.lower()
        for i, w in enumerate(words)
    )


def _extract_caption(description: str) -> str | None:
    m = _CAPTION_RE.search(description)
    if not m:
        return None
    caption = m.group(1).strip()
    # Discard very long captions (they're sentence descriptions, not meme names).
    if len(caption) > 60:
        return None
    # Capitalize first letter while preserving the rest of the casing.
    return caption[0].upper() + caption[1:] if caption else caption


def _make_title(slug: str, description: str, locale: str) -> dict[str, str]:
    """Return {"title": ...} or {"title_en": ..., "title_es": ...}."""
    slug_title = _slug_to_title(slug)
    caption = _extract_caption(description)

    if locale == "intl":
        return {
            "title_en": slug_title,
            "title_es": caption or slug_title,
        }
    if locale == "es":
        return {"title": caption or slug_title}
    # "en"
    return {"title": slug_title}


# ── MDX I/O ──────────────────────────────────────────────────────────────────


def parse_mdx(path: Path) -> tuple[dict[str, Any], str] | None:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines(keepends=True)
    if not lines or lines[0].strip() != FRONTMATTER_DELIMITER:
        return None
    end = next(
        (i for i in range(1, len(lines)) if lines[i].strip() == FRONTMATTER_DELIMITER),
        -1,
    )
    if end < 0:
        return None
    fm = yaml.safe_load("".join(lines[1:end])) or {}
    body = "".join(lines[end + 1 :])
    return fm, body


def write_mdx(fm: dict[str, Any], body: str) -> str:
    fm_yaml = yaml.safe_dump(
        fm, sort_keys=False, allow_unicode=True, default_flow_style=False
    )
    return f"{FRONTMATTER_DELIMITER}\n{fm_yaml}{FRONTMATTER_DELIMITER}\n{body}"


def detect_locale(fm: dict[str, Any]) -> str:
    t = fm.get("translations", {})
    if "en" in t and "es" in t:
        return "intl"
    return "es" if "es" in t else "en"


def transform(fm: dict[str, Any]) -> dict[str, Any]:
    locale = detect_locale(fm)
    translations = {k: dict(v) for k, v in fm.get("translations", {}).items()}

    slug: str = fm.get("slug", "")
    data = translations.get("es") or translations.get("en") or {}
    description: str = data.get("description", "") or ""

    titles = _make_title(slug, description, locale)

    if locale == "intl":
        translations["en"]["title"] = titles["title_en"]
        translations["es"]["title"] = titles["title_es"]
    elif locale == "es":
        translations["es"]["title"] = titles["title"]
    else:
        translations["en"]["title"] = titles["title"]

    new_fm = dict(fm)
    new_fm["translations"] = translations
    return new_fm


# ── CLI ───────────────────────────────────────────────────────────────────────


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, help="memes/ directory to walk")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not args.root.is_dir():
        print(f"Not a directory: {args.root}", file=sys.stderr)
        return 1

    updated = skipped = 0

    for mdx in sorted(args.root.rglob("*.mdx")):
        parsed = parse_mdx(mdx)
        if parsed is None:
            skipped += 1
            continue
        fm, body = parsed
        new_fm = transform(fm)
        rendered = write_mdx(new_fm, body)

        if args.dry_run:
            locale = detect_locale(fm)
            t = new_fm["translations"]
            if locale == "intl":
                print(f"[intl] {mdx.name}")
                print(f"  en: {t['en']['title']!r}")
                print(f"  es: {t['es']['title']!r}")
            else:
                key = "es" if "es" in t else "en"
                print(f"[{locale}] {mdx.name}: {t[key]['title']!r}")
        else:
            mdx.write_text(rendered, encoding="utf-8")
        updated += 1

    print(f"Done. updated={updated} skipped={skipped}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
