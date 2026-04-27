#!/usr/bin/env python3
"""Auto-translate Argentina-related memes into Spanish (Argentina / rioplatense).

For each MDX file under the given root, the script:

1. Parses the frontmatter (must be V2 — use scripts/migrate_mdx_to_v2.py first).
2. Decides whether the meme is Argentina-related using these signals:
     * subreddit is one of the Argentine subs (configurable via --subreddits)
     * category contains "argentina" / "argentino" / "argento"
     * any tag is "argentina", starts with "argentina-", or contains "argentino"
3. If yes and a Spanish translation isn't already present, calls the `claude`
   CLI to produce a rioplatense translation of title + description.
4. Writes the new `es` entry into the `translations:` block in place.

The script is idempotent — files that already have a non-empty `es`
translation are skipped. Use --force to re-translate.

Prerequisites:
  * `claude` CLI on PATH (https://docs.claude.com/claude-code).
  * Python 3.10+, PyYAML.

Usage:
  python scripts/translate_argentina_memes.py memes/
  python scripts/translate_argentina_memes.py memes/ --dry-run --limit 5
  python scripts/translate_argentina_memes.py memes/ --workers 4 --verbose
  python scripts/translate_argentina_memes.py memes/ --force
"""
from __future__ import annotations

import argparse
import json
import logging
import subprocess
import sys
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


FRONTMATTER_DELIMITER = "---"
TARGET_LOCALE = "es"
DEFAULT_ARGENTINA_SUBREDDITS = {
    "argentina",
    "republicaargentina",
    "dankgentina",
    "argentinabenderstyle",
    "rosario",
    "buenosaires",
}
ARGENTINA_TAG_HINTS = ("argentina", "argentino", "argento", "rioplatense")

CLAUDE_TIMEOUT_SECONDS = 120
CLAUDE_RETRIES = 2

log = logging.getLogger("translate")


# ============================================================================
# Heuristics
# ============================================================================

def is_argentina_related(fm: dict[str, Any], allowed_subs: set[str]) -> bool:
    sub = str(fm.get("subreddit") or "").strip().lower()
    if sub in allowed_subs:
        return True

    category = str(fm.get("category") or "").lower()
    if any(hint in category for hint in ARGENTINA_TAG_HINTS):
        return True

    tags = fm.get("tags") or []
    if isinstance(tags, list):
        for tag in tags:
            t = str(tag).lower().strip()
            if t in ("argentina", "argentino", "argento"):
                return True
            if any(t.startswith(h + "-") or t.endswith("-" + h) for h in ARGENTINA_TAG_HINTS):
                return True
            if any(h in t for h in ARGENTINA_TAG_HINTS):
                return True
    return False


# ============================================================================
# MDX I/O
# ============================================================================

@dataclass
class ParsedMdx:
    frontmatter: dict[str, Any]
    body: str
    fm_block_lines: tuple[int, int]  # (start_inclusive, end_exclusive) of YAML body in file


def parse_mdx(path: Path) -> ParsedMdx | None:
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
    body = "".join(lines[end + 1:])
    return ParsedMdx(frontmatter=fm, body=body, fm_block_lines=(1, end))


def render_mdx(parsed: ParsedMdx) -> str:
    fm_yaml = yaml.safe_dump(
        parsed.frontmatter,
        sort_keys=False,
        allow_unicode=True,
        default_flow_style=False,
        width=120,
    )
    return f"{FRONTMATTER_DELIMITER}\n{fm_yaml}{FRONTMATTER_DELIMITER}\n{parsed.body}"


def existing_es_translation(translations: dict[str, Any] | None) -> dict[str, Any] | None:
    if not isinstance(translations, dict):
        return None
    es = translations.get(TARGET_LOCALE)
    if not isinstance(es, dict):
        return None
    title = str(es.get("title") or "").strip()
    return es if title else None


def insert_translation_after_default(
    translations: dict[str, Any], default_locale: str, locale: str, payload: dict[str, Any]
) -> dict[str, Any]:
    """Return a new ordered dict with `locale` placed right after `default_locale`."""
    out: dict[str, Any] = {}
    inserted = False
    for k, v in translations.items():
        out[k] = v
        if k == default_locale and not inserted:
            out[locale] = payload
            inserted = True
    if not inserted:
        out[locale] = payload
    return out


# ============================================================================
# Translation via claude CLI
# ============================================================================

_PROMPT = """\
You are translating meme metadata for an Argentine audience.

Translate the title and description below into rioplatense Spanish (the variety \
spoken in Buenos Aires and the rest of Argentina).

Style requirements:
- Use voseo where natural ("vos tenés", "vos querés", not "tú tienes").
- Argentine vocabulary and slang where it fits ("laburo", "boludo", "che", "guita") \
  but stay tasteful — these are public-facing meme captions.
- Keep proper nouns (names, brands, sports clubs) untranslated.
- Preserve any meme-internal references; don't over-explain jokes.
- Match the original tone: sarcastic stays sarcastic, deadpan stays deadpan.
- Title <= 200 chars, description <= 1000 chars.
- If the description is empty/null, return null for description.

Return ONLY a JSON object — no markdown, no code fences, no commentary:
{{"title": "...", "description": "..."}}

INPUT
Title: {title}
Description: {description}
"""


_claude_lock = threading.Lock()  # serialize FileNotFoundError detection only


def translate_via_claude(title: str, description: str | None) -> dict[str, Any] | None:
    desc_text = description if description is not None else ""
    prompt = _PROMPT.format(
        title=json.dumps(title, ensure_ascii=False),
        description=json.dumps(desc_text, ensure_ascii=False),
    )

    for attempt in range(CLAUDE_RETRIES):
        try:
            proc = subprocess.run(
                [
                    "claude",
                    "-p", prompt,
                    "--dangerously-skip-permissions",
                    "--output-format", "text",
                ],
                capture_output=True,
                text=True,
                timeout=CLAUDE_TIMEOUT_SECONDS,
            )
        except FileNotFoundError:
            with _claude_lock:
                log.error("'claude' CLI not found on PATH — install Claude Code first")
            return None
        except subprocess.TimeoutExpired:
            log.warning("claude CLI timed out (attempt %d)", attempt + 1)
            continue

        if proc.returncode != 0:
            log.warning("claude CLI error (attempt %d): %s", attempt + 1, proc.stderr[:200])
            continue

        raw = proc.stdout.strip()
        start = raw.find("{")
        end = raw.rfind("}") + 1
        if start == -1 or end == 0:
            log.warning("No JSON in claude output (attempt %d): %r", attempt + 1, raw[:300])
            continue

        try:
            parsed = json.loads(raw[start:end])
        except json.JSONDecodeError as e:
            log.warning("JSON parse error (attempt %d): %s", attempt + 1, e)
            continue

        translated_title = str(parsed.get("title") or "").strip()
        if not translated_title:
            log.warning("Translation returned empty title (attempt %d)", attempt + 1)
            continue
        translated_desc = parsed.get("description")
        if isinstance(translated_desc, str):
            translated_desc = translated_desc.strip() or None
        elif translated_desc is not None:
            translated_desc = str(translated_desc).strip() or None

        out: dict[str, Any] = {"title": translated_title}
        if translated_desc is not None:
            out["description"] = translated_desc
        return out

    return None


# ============================================================================
# Per-file pipeline
# ============================================================================

@dataclass
class TranslationOutcome:
    path: Path
    status: str  # one of: translated, skipped_existing, skipped_not_argentina, skipped_no_translations, failed, dry_run
    detail: str | None = None


def process_file(path: Path, allowed_subs: set[str], dry_run: bool, force: bool) -> TranslationOutcome:
    parsed = parse_mdx(path)
    if parsed is None:
        return TranslationOutcome(path, "skipped_no_frontmatter")

    fm = parsed.frontmatter
    if not is_argentina_related(fm, allowed_subs):
        return TranslationOutcome(path, "skipped_not_argentina")

    translations = fm.get("translations")
    if not isinstance(translations, dict) or not translations:
        return TranslationOutcome(path, "skipped_no_translations",
                                  "run scripts/migrate_mdx_to_v2.py first")

    if not force and existing_es_translation(translations) is not None:
        return TranslationOutcome(path, "skipped_existing")

    default_locale = str(fm.get("default_locale") or "en")
    source = translations.get(default_locale) or next(iter(translations.values()))
    if not isinstance(source, dict):
        return TranslationOutcome(path, "failed", "default locale entry is not a mapping")
    title = str(source.get("title") or "").strip()
    if not title:
        return TranslationOutcome(path, "failed", "default locale entry has empty title")
    description = source.get("description")
    description = str(description).strip() if description is not None else None

    if dry_run:
        return TranslationOutcome(path, "dry_run", f"would translate: {title!r}")

    translated = translate_via_claude(title, description)
    if translated is None:
        return TranslationOutcome(path, "failed", "claude CLI did not return a usable translation")

    new_translations = insert_translation_after_default(
        translations, default_locale, TARGET_LOCALE, translated
    )
    fm["translations"] = new_translations
    path.write_text(render_mdx(parsed), encoding="utf-8")
    return TranslationOutcome(path, "translated", translated["title"])


# ============================================================================
# CLI
# ============================================================================

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", type=Path, help="memes/ directory to walk")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print plan without writing files or calling claude.")
    parser.add_argument("--force", action="store_true",
                        help="Re-translate even when an `es` entry already exists.")
    parser.add_argument("--limit", type=int, default=0,
                        help="Stop after this many translations (0 = no limit).")
    parser.add_argument("--workers", type=int, default=2,
                        help="Concurrent claude CLI invocations (default: 2).")
    parser.add_argument("--subreddits", type=str, default=None,
                        help=("Comma-separated subreddits considered Argentine. "
                              "Defaults to: " + ",".join(sorted(DEFAULT_ARGENTINA_SUBREDDITS))))
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )

    if not args.root.is_dir():
        log.error("Not a directory: %s", args.root)
        return 1

    allowed_subs = {s.strip().lower() for s in (args.subreddits or "").split(",") if s.strip()}
    if not allowed_subs:
        allowed_subs = set(DEFAULT_ARGENTINA_SUBREDDITS)

    mdx_files = sorted(args.root.rglob("*.mdx"))
    log.info("Found %d MDX files under %s", len(mdx_files), args.root)

    # First pass: classify (cheap) so we can show a plan and respect --limit.
    plan: list[Path] = []
    counters: dict[str, int] = {}
    for path in mdx_files:
        parsed = parse_mdx(path)
        if parsed is None:
            counters["skipped_no_frontmatter"] = counters.get("skipped_no_frontmatter", 0) + 1
            continue
        if not is_argentina_related(parsed.frontmatter, allowed_subs):
            counters["skipped_not_argentina"] = counters.get("skipped_not_argentina", 0) + 1
            continue
        translations = parsed.frontmatter.get("translations")
        if not isinstance(translations, dict) or not translations:
            counters["skipped_no_translations"] = counters.get("skipped_no_translations", 0) + 1
            continue
        if not args.force and existing_es_translation(translations) is not None:
            counters["skipped_existing"] = counters.get("skipped_existing", 0) + 1
            continue
        plan.append(path)
        if args.limit and len(plan) >= args.limit:
            break

    log.info("Plan: %d files to translate, counters=%s", len(plan), counters)
    if not plan:
        return 0

    if args.dry_run:
        for p in plan:
            log.info("[dry-run] would translate %s", p)
        return 0

    # Second pass: translate concurrently.
    translated = failed = 0
    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as pool:
        futures = {pool.submit(process_file, p, allowed_subs, False, args.force): p for p in plan}
        for fut in as_completed(futures):
            outcome = fut.result()
            if outcome.status == "translated":
                translated += 1
                log.info("✓ %s — %s", outcome.path.relative_to(args.root), outcome.detail)
            else:
                failed += 1
                log.warning("✗ %s — %s: %s", outcome.path.relative_to(args.root),
                            outcome.status, outcome.detail or "")

    log.info("Done. translated=%d failed=%d", translated, failed)
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
