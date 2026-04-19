import json
import logging
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

logger = logging.getLogger(__name__)

_PROMPT = """\
Read this image file: {image_path}

Analyze it and determine if it is a meme. A meme is a humorous or culturally \
significant image — this includes reaction images, recognizable internet templates \
(Simpsons frames, Pepe, Wojak, Drake format, etc.), images with text overlays \
intended to be funny, and viral image formats. News photos, plain screenshots, \
logos, food photos, selfies, or generic photos are NOT memes.

Respond with ONLY a valid JSON object — no markdown, no code fences, no explanation:
{{"is_meme": true, "category": "simpsons", "filename_slug": "homer-walks-into-bar", "description": "Homer Simpson enters a bar looking confused"}}

Field rules:
- is_meme: boolean
- category: free-form lowercase label describing the meme type (e.g. "simpsons", \
"pepe", "wojak", "argentina-politics", "reaction", "surreal", "doomer", "drake-format"). \
Be specific. Empty string "" if not a meme.
- filename_slug: 3-7 words in kebab-case describing the specific content \
(e.g. "homer-walks-into-lesbian-bar", "pepe-crying-at-boca-loss"). \
Empty string "" if not a meme.
- description: one sentence. Empty string "" if not a meme.
"""


@dataclass
class ClassificationResult:
    url: str
    is_meme: bool = False
    category: str = ""
    filename_slug: str = ""
    description: str = ""
    error: str | None = None


def classify_image(image_path: Path, url: str) -> ClassificationResult:
    prompt = _PROMPT.format(image_path=str(image_path))

    for attempt in range(2):
        try:
            proc = subprocess.run(
                [
                    "claude",
                    "-p", prompt,
                    "--dangerously-skip-permissions",
                    "--tools", "Read",
                    "--output-format", "text",
                    "--no-session-persistence",
                ],
                capture_output=True,
                text=True,
                timeout=120,
            )

            if proc.returncode != 0:
                logger.warning("claude CLI error (attempt %d): %s", attempt + 1, proc.stderr[:200])
                continue

            raw = proc.stdout.strip()
            start = raw.find("{")
            end = raw.rfind("}") + 1
            if start == -1 or end == 0:
                logger.warning("No JSON found in claude output (attempt %d): %r", attempt + 1, raw[:300])
                continue

            parsed = json.loads(raw[start:end])
            return ClassificationResult(
                url=url,
                is_meme=bool(parsed.get("is_meme", False)),
                category=str(parsed.get("category", "")).strip().lower(),
                filename_slug=str(parsed.get("filename_slug", "")).strip().lower(),
                description=str(parsed.get("description", "")).strip(),
            )

        except json.JSONDecodeError as e:
            logger.warning("JSON parse error (attempt %d): %s", attempt + 1, e)
        except subprocess.TimeoutExpired:
            logger.warning("claude CLI timed out for %s", image_path)
            break
        except FileNotFoundError:
            logger.error("'claude' command not found — is Claude Code installed?")
            return ClassificationResult(url=url, error="claude_not_found")

    return ClassificationResult(url=url, error="classification_failed")


def classify_batch(items: list[tuple[str, Path]]) -> list[ClassificationResult]:
    results = []
    for url, path in items:
        logger.info("Classifying %s", path.name)
        result = classify_image(path, url)
        if result.error:
            logger.warning("Classification error for %s: %s", url, result.error)
        else:
            logger.info("  → is_meme=%s category=%r slug=%r", result.is_meme, result.category, result.filename_slug)
        results.append(result)
    return results
