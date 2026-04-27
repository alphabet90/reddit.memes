#!/usr/bin/env python3
"""Apply locale-based translation classification to V2 MDX files.

Business rules
--------------
1. If the meme has Argentine/Spanish context (argentina subreddit, Spanish text
   caption, or argentina category/subcategory) → replace the "en" translation key
   with "es" and set default_locale to "es".
2. If the meme is also internationally recognised (world-famous character with no
   text caption) → keep BOTH "en" and "es" translations (default_locale: en).
3. If a file already carries an "es" key and no "en" key it is skipped (idempotent).

Locale detection is done purely from frontmatter metadata — no image scanning.

Usage
-----
    python scripts/apply_locale_translations.py memes/
    python scripts/apply_locale_translations.py memes/ --dry-run
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

import yaml

FRONTMATTER_DELIMITER = "---"

# ── Locale detection patterns ─────────────────────────────────────────────────

_SPANISH_ACCENT_RE = re.compile(r"[áéíóúñüÁÉÍÓÚÑÜ¿¡]")

_SPANISH_WORDS_RE = re.compile(
    r"\b("
    # Core function words
    r"del|los|las|pero|para|que|con|por|hay|muy|sin|sobre|hasta|desde|hacia|"
    r"porque|cuando|donde|"
    # Verbs
    r"vivo|viva|tiene|tienen|tengo|soy|somos|estoy|dijo|dice|fue|fui|son|"
    r"van|ven|voy|vamos|viene|vienen|pasa|"
    # Pronouns / determiners
    r"todo|nada|algo|muchos|muchas|ellos|ellas|nosotros|"
    r"este|esta|estos|estas|ese|esa|eso|esos|esas|unos|unas|"
    # Adverbs / connectors
    r"también|tampoco|siempre|nunca|acá|allá|aquí|así|solo|"
    # Argentine/Spanish-specific nouns & adjectives
    r"hermanos|mundial|queso|cosa|linda|beso|cuarto|oscuro|"
    r"jubilados|jubilado|basados|basado|campeon|campeona|"
    r"bicampeon|bicampeona|perdí|perdi|visto|juntos|junto|cine|"
    # Unstressed particles (kept because whole-word match avoids English collisions)
    r"al|lo|la|de|el|en|mi|su|tu|te|me|le|nos|les|más|bien|mal|"
    r"cómo|qué|cuál|quién"
    r")\b",
    re.IGNORECASE,
)

_ARGENTINA_RE = re.compile(r"argentina|fulbo|dankgentina", re.IGNORECASE)

_SPANISH_TEXT_IN_DESC_RE = re.compile(
    r"Spanish text|texto en español|texto|spanish caption", re.IGNORECASE
)

_TEXT_OVERLAY_IN_DESC_RE = re.compile(
    r"\bwith (?:the )?text\b|text overlaid|\bcaption\b", re.IGNORECASE
)


# ── Classification helpers ────────────────────────────────────────────────────


def _has_argentina_context(fm: dict[str, Any], title: str, desc: str) -> bool:
    """True when any signal points to Argentine / Spanish-language content."""
    if _ARGENTINA_RE.search(str(fm.get("category", ""))):
        return True
    if _ARGENTINA_RE.search(str(fm.get("subreddit", ""))):
        return True
    tags = fm.get("tags", [])
    if isinstance(tags, list) and any(_ARGENTINA_RE.search(str(t)) for t in tags):
        return True
    if _SPANISH_ACCENT_RE.search(title) or _SPANISH_WORDS_RE.search(title):
        return True
    if _SPANISH_TEXT_IN_DESC_RE.search(desc):
        return True
    return False


def _is_international_template(fm: dict[str, Any], title: str, desc: str) -> bool:
    """True when the meme is a world-famous template with no text caption.

    Conditions (all must hold):
    - Category is not Argentina/fulbo-specific.
    - Title contains no Spanish accents or common Spanish words.
    - Description does not mention Spanish text or any text overlay.
    """
    if _ARGENTINA_RE.search(str(fm.get("category", ""))):
        return False
    if _SPANISH_ACCENT_RE.search(title) or _SPANISH_WORDS_RE.search(title):
        return False
    if _SPANISH_TEXT_IN_DESC_RE.search(desc) or _TEXT_OVERLAY_IN_DESC_RE.search(desc):
        return False
    return True


# ── MDX parsing & serialisation ───────────────────────────────────────────────


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


# ── Transformation ────────────────────────────────────────────────────────────


def transform(fm: dict[str, Any]) -> dict[str, Any] | None:
    """Return updated frontmatter or None if the file is already correctly localised."""
    translations: dict[str, Any] = fm.get("translations", {})

    # Idempotency: already has "es" and no bare "en" → done.
    if "es" in translations and "en" not in translations:
        return None

    en_data: dict[str, Any] = translations.get("en", {})
    title: str = en_data.get("title", "") or ""
    desc: str = en_data.get("description", "") or ""

    argentina = _has_argentina_context(fm, title, desc)
    international = _is_international_template(fm, title, desc)

    new_fm = dict(fm)

    if international:
        # World-famous template (with or without Argentine context): provide both
        # translations so the record is usable in any locale context.
        new_fm["default_locale"] = "en"
        new_fm["translations"] = {"en": dict(en_data), "es": dict(en_data)}
    elif argentina:
        # Pure Argentine / Spanish content → "es" only.
        new_fm["default_locale"] = "es"
        new_fm["translations"] = {"es": dict(en_data)}
    else:
        # Fallback (should not occur for this dataset): keep "en".
        return None

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

    counts: dict[str, int] = {
        "es_only": 0,
        "intl_both": 0,
        "skipped": 0,
        "no_fm": 0,
    }

    for mdx in sorted(args.root.rglob("*.mdx")):
        parsed = parse_mdx(mdx)
        if parsed is None:
            counts["no_fm"] += 1
            continue

        fm, body = parsed
        new_fm = transform(fm)
        if new_fm is None:
            counts["skipped"] += 1
            continue

        translations = new_fm.get("translations", {})
        if "en" in translations and "es" in translations:
            counts["intl_both"] += 1
            label = "INTL(en+es)"
        else:
            counts["es_only"] += 1
            label = "es"

        rendered = write_mdx(new_fm, body)
        if args.dry_run:
            print(f"--- [{label}] {mdx} ---")
            print(rendered[:500])
            print("...\n")
        else:
            mdx.write_text(rendered, encoding="utf-8")

    print(
        f"Done. es_only={counts['es_only']} intl_both={counts['intl_both']} "
        f"skipped={counts['skipped']} no_fm={counts['no_fm']}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
