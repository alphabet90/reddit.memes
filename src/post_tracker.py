"""Bloom-filter–backed tracker for processed Reddit post IDs and image URLs.

Replaces the previous `state.json` lookup entirely. The on-disk representation
is a single binary Bloom filter file.
"""

from __future__ import annotations

import json
import logging
from pathlib import Path

from src.bloom import BloomFilter, BloomFilterError

logger = logging.getLogger(__name__)


class PostTracker:
    """Membership-only tracker for post IDs and image URLs.

    Both namespaces share one Bloom filter: post IDs look like `t3_abc123`
    and image URLs always start with `http`, so collisions between the two
    namespaces are impossible in practice.
    """

    def __init__(
        self,
        path: Path,
        capacity: int = 200_000,
        error_rate: float = 1e-3,
    ) -> None:
        self._path = path
        self._capacity = capacity
        self._error_rate = error_rate
        self._bloom: BloomFilter = self._load_or_create()

    def _load_or_create(self) -> BloomFilter:
        if self._path.exists():
            try:
                bloom = BloomFilter.load(self._path)
                logger.info(
                    "Loaded Bloom filter from %s (%d items, m=%d bits, k=%d)",
                    self._path.name, len(bloom), bloom.capacity_bits, bloom.hash_count,
                )
                return bloom
            except BloomFilterError as e:
                logger.warning("Bloom filter at %s is corrupt (%s) — starting fresh", self._path, e)
        return BloomFilter(capacity=self._capacity, error_rate=self._error_rate)

    # ------------------------------------------------------------ membership

    def is_processed(self, key: str) -> bool:
        return key in self._bloom

    def mark_processed(self, key: str) -> None:
        self._bloom.add(key)

    def __len__(self) -> int:
        return len(self._bloom)

    # ------------------------------------------------------------ persistence

    def flush(self) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        self._bloom.save(self._path)

    # --------------------------------------------------------- content hashing

    _SHA1_PREFIX = "sha1:"

    def is_content_processed(self, sha1_hex: str) -> bool:
        return self.is_processed(f"{self._SHA1_PREFIX}{sha1_hex}")

    def mark_content_processed(self, sha1_hex: str) -> None:
        self.mark_processed(f"{self._SHA1_PREFIX}{sha1_hex}")

    @property
    def metadata(self) -> dict:
        return self._bloom.metadata

    # ------------------------------------------------------------- migration

    def migrate_from_state_json(self, state_file: Path) -> int:
        """Import a legacy state.json into the Bloom filter. Returns count
        of items imported. Safe to call when the file doesn't exist."""
        if not state_file.exists():
            return 0
        try:
            with open(state_file) as f:
                data = json.load(f)
        except (json.JSONDecodeError, OSError) as e:
            logger.warning("Could not read legacy state file %s: %s", state_file, e)
            return 0

        imported = 0
        for post_id in data.get("processed_post_ids", {}):
            self._bloom.add(post_id)
            imported += 1
        for url in data.get("processed_urls", {}):
            self._bloom.add(url)
            imported += 1

        logger.info("Migrated %d items from %s into Bloom filter", imported, state_file.name)
        return imported
