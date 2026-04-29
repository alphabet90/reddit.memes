import json
import logging
import subprocess
from pathlib import Path

from src.classifiers.base import BaseClassifier, ClassificationResult

logger = logging.getLogger(__name__)


class ClaudeClassifier(BaseClassifier):

    def __init__(self, locale: str = "en", prompts_dir: Path | None = None) -> None:
        super().__init__(locale, prompts_dir)

    def classify_image(self, image_path: Path, url: str) -> ClassificationResult:
        prompt = self._prompt.format(image_path=str(image_path))

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
                    title=str(parsed.get("title", "")).strip(),
                    category=str(parsed.get("category", "")).strip().lower(),
                    filename_slug=str(parsed.get("filename_slug", "")).strip().lower(),
                    description=str(parsed.get("description", "")).strip(),
                    tags=[str(t).strip().lower() for t in parsed.get("tags", []) if t],
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
