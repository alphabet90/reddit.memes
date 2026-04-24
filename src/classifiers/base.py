import logging
from abc import ABC, abstractmethod
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from pathlib import Path

import config

logger = logging.getLogger(__name__)


@dataclass
class ClassificationResult:
    url: str
    is_meme: bool = False
    category: str = ""
    filename_slug: str = ""
    description: str = ""
    error: str | None = None


class BaseClassifier(ABC):

    @abstractmethod
    def classify_image(self, image_path: Path, url: str) -> ClassificationResult:
        ...

    def classify_batch(
        self,
        items: list[tuple[str, Path]],
        max_workers: int | None = None,
    ) -> list[ClassificationResult]:
        workers = max_workers if max_workers is not None else config.CLASSIFY_WORKERS

        def _classify_one(item: tuple[str, Path]) -> ClassificationResult:
            url, path = item
            logger.info("Classifying %s", path.name)
            try:
                result = self.classify_image(path, url)
            except Exception as e:
                logger.error("Unexpected error classifying %s: %s", path.name, e)
                result = ClassificationResult(url=url, error=f"unexpected: {e}")
            if result.error:
                logger.warning("  [error] %s: %s", path.name, result.error)
            else:
                logger.info(
                    "  → %s: is_meme=%s category=%r slug=%r",
                    path.name, result.is_meme, result.category, result.filename_slug,
                )
            return result

        with ThreadPoolExecutor(max_workers=workers) as executor:
            return list(executor.map(_classify_one, items))
