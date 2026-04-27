#!/usr/bin/env python3
"""Generate canonical meme titles from descriptions using the Claude CLI.

The title field currently holds the Reddit post title, which is wrong.
This script derives the actual meme name from the description + slug,
producing locale-appropriate titles:

  - "es" locale  → Spanish meme name (or English if that's the common name in AR)
  - "en" locale  → English meme name
  - "intl" (both en+es) → two titles, one per locale

Usage:
    python scripts/generate_meme_titles.py memes/
    python scripts/generate_meme_titles.py memes/ --dry-run
    python scripts/generate_meme_titles.py memes/ --batch-size 40
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

import yaml

FRONTMATTER_DELIMITER = "---"

_SYSTEM_PROMPT = """\
You are an expert in internet memes and Argentine pop culture.
You will receive a JSON array of meme records. For each record return the
CANONICAL NAME of the meme template — NOT the Reddit post title used to share it.

Rules:
- The name identifies the meme FORMAT/TEMPLATE, not the specific Argentine usage.
- Keep titles SHORT: 2–6 words, title-cased.
- For locale "es": use the name as known in Argentina/Spanish-speaking context.
  Use Spanish when that is the natural name (e.g. "La Gaviota Inhalando").
  Use English when the meme is universally known by its English name (e.g. "Always Has Been").
  If the meme is defined by a Spanish text caption shown in the image, use that caption.
- For locale "en": use the canonical English meme name.
- For locale "intl": provide BOTH title_en and title_es.
- Return ONLY a JSON array — no markdown fences, no explanation.

Output schema per item:
  locale "es" or "en": {"slug": "...", "title": "..."}
  locale "intl":        {"slug": "...", "title_en": "...", "title_es": "..."}
"""


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


def call_claude(records: list[dict]) -> list[dict]:
    """Send a batch to the claude CLI and return the parsed JSON list."""
    records_json = json.dumps(records, ensure_ascii=False, indent=2)
    prompt = (
        f"{_SYSTEM_PROMPT}\n\nMeme records:\n{records_json}"
    )

    for attempt in range(3):
        try:
            proc = subprocess.run(
                [
                    "claude",
                    "-p", prompt,
                    "--dangerously-skip-permissions",
                    "--output-format", "text",
                    "--no-session-persistence",
                ],
                capture_output=True,
                text=True,
                timeout=180,
            )
            if proc.returncode != 0:
                raise RuntimeError(proc.stderr[:300])

            raw = proc.stdout.strip()
            # Strip markdown code fences if present
            if raw.startswith("```"):
                raw = raw.split("\n", 1)[1]
                raw = raw.rsplit("```", 1)[0]

            return json.loads(raw)
        except json.JSONDecodeError as exc:
            if attempt == 2:
                raise RuntimeError(f"JSON parse error: {exc}\nRaw output:\n{raw[:500]}")
        except subprocess.TimeoutExpired:
            if attempt == 2:
                raise RuntimeError("claude CLI timed out")
        except RuntimeError:
            if attempt == 2:
                raise

        wait = 2 ** attempt
        print(f"    retrying in {wait}s…", file=sys.stderr)
        time.sleep(wait)

    return []


def apply_titles(
    fm: dict[str, Any],
    body: str,
    result: dict,
    locale: str,
) -> str:
    new_fm = dict(fm)
    translations = {k: dict(v) for k, v in fm.get("translations", {}).items()}

    if locale == "intl":
        title_en = result.get("title_en") or result.get("title", "")
        title_es = result.get("title_es") or result.get("title", "")
        translations.setdefault("en", {})["title"] = title_en
        translations.setdefault("es", {})["title"] = title_es
    elif locale == "es":
        translations.setdefault("es", {})["title"] = result.get("title", "")
    else:
        translations.setdefault("en", {})["title"] = result.get("title", "")

    new_fm["translations"] = translations
    return write_mdx(new_fm, body)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, help="memes/ directory to walk")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--batch-size", type=int, default=40)
    args = parser.parse_args()

    if not args.root.is_dir():
        print(f"Not a directory: {args.root}", file=sys.stderr)
        return 1

    all_files: list[tuple[Path, dict[str, Any], str, str]] = []
    for mdx in sorted(args.root.rglob("*.mdx")):
        parsed = parse_mdx(mdx)
        if parsed is None:
            continue
        fm, body = parsed
        all_files.append((mdx, fm, body, detect_locale(fm)))

    total = len(all_files)
    total_batches = (total + args.batch_size - 1) // args.batch_size
    print(f"Processing {total} files in {total_batches} batches of {args.batch_size}…",
          file=sys.stderr)

    updated = errors = 0

    for batch_start in range(0, total, args.batch_size):
        batch = all_files[batch_start: batch_start + args.batch_size]
        batch_num = batch_start // args.batch_size + 1
        print(f"  Batch {batch_num}/{total_batches} ({len(batch)} files)…",
              file=sys.stderr)

        records = []
        for path, fm, body, locale in batch:
            t = fm.get("translations", {})
            data = t.get("es") or t.get("en") or {}
            records.append({
                "slug": fm["slug"],
                "category": fm.get("category", ""),
                "locale": locale,
                "description": data.get("description", ""),
            })

        try:
            results = call_claude(records)
        except Exception as exc:
            print(f"  ERROR in batch {batch_num}: {exc}", file=sys.stderr)
            errors += len(batch)
            continue

        result_map = {r["slug"]: r for r in results}

        for path, fm, body, locale in batch:
            slug = fm["slug"]
            result = result_map.get(slug)
            if not result:
                print(f"  WARN: missing result for {slug}", file=sys.stderr)
                errors += 1
                continue

            rendered = apply_titles(fm, body, result, locale)

            if args.dry_run:
                t = (fm.get("translations", {}).get("es")
                     or fm.get("translations", {}).get("en") or {})
                new_title = result.get("title") or result.get("title_es", "")
                old_title = t.get("title", "")[:50]
                print(f"  [{locale}] {path.name}")
                print(f"    was: {old_title!r}")
                print(f"    now: {new_title!r}")
            else:
                path.write_text(rendered, encoding="utf-8")
            updated += 1

        if batch_start + args.batch_size < total:
            time.sleep(0.3)

    print(f"Done. updated={updated} errors={errors}", file=sys.stderr)
    return 0 if errors == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
