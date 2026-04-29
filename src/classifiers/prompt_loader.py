import logging
from pathlib import Path

logger = logging.getLogger(__name__)

_PROMPTS_DIR = Path(__file__).parent.parent.parent / "prompts"


def _normalize(locale: str) -> str:
    """Normalize locale string to BCP 47 with hyphen (e.g. 'es_AR' → 'es-AR', 'EN' → 'en')."""
    parts = locale.replace("-", "_").split("_", 1)
    lang = parts[0].lower()
    if len(parts) == 2:
        return f"{lang}-{parts[1].upper()}"
    return lang


def load_prompt(locale: str, prompts_dir: Path | None = None) -> str:
    """Return the prompt template for the given locale with fallback to 'en'.

    Resolution order:
      1. prompt.{locale}.txt          (e.g. prompt.es-AR.txt)
      2. prompt.{language}.txt        (e.g. prompt.es.txt)
      3. prompt.en.txt
    """
    base = prompts_dir or _PROMPTS_DIR
    normalized = _normalize(locale)
    lang = normalized.split("-")[0]

    candidates: list[Path] = [base / f"prompt.{normalized}.txt"]
    if lang != normalized:
        candidates.append(base / f"prompt.{lang}.txt")
    if normalized != "en" and lang != "en":
        candidates.append(base / "prompt.en.txt")

    for path in candidates:
        if path.exists():
            if path != candidates[0]:
                logger.warning(
                    "Prompt for locale %r not found, falling back to %s",
                    locale,
                    path.name,
                )
            return path.read_text(encoding="utf-8")

    raise FileNotFoundError(
        f"No prompt file found for locale {locale!r}. "
        f"Searched: {[str(p) for p in candidates]}"
    )
