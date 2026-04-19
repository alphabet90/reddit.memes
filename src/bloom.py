"""Reusable Bloom filter with atomic disk persistence.

A Bloom filter is a space-efficient probabilistic set. It answers
`x in filter` in O(k) time using a bit array and `k` hash functions.

Guarantees:
- False negatives are impossible: if `x in filter` returns False, `x` was
  never added.
- False positives are possible at the configured `error_rate`.

The implementation is stdlib-only and generic over `str | bytes`. It is
intentionally decoupled from the reddit.memes domain so it can be reused
in any project that needs cheap membership tests.
"""

from __future__ import annotations

import hashlib
import json
import math
import os
import struct
from pathlib import Path

_MAGIC = b"BLM1"
_FORMAT_VERSION = 1
_HEADER_STRUCT = struct.Struct("<4sIQIIQ")  # magic, version, m, k, count, meta_len


class BloomFilterError(Exception):
    """Raised for malformed Bloom filter files."""


class BloomFilter:
    """A Bloom filter with double-hashing and a simple binary file format.

    Parameters
    ----------
    capacity: Expected number of items. Drives sizing of the bit array.
    error_rate: Target false-positive probability when the filter holds
        `capacity` items.
    metadata: Optional small JSON-serializable dict persisted alongside the
        bits. Useful for piggybacking cursor/version info onto a single
        state artifact.
    """

    __slots__ = ("_m", "_k", "_bits", "_count", "_metadata")

    def __init__(
        self,
        capacity: int = 100_000,
        error_rate: float = 1e-3,
        metadata: dict | None = None,
    ) -> None:
        if capacity <= 0:
            raise ValueError("capacity must be positive")
        if not 0.0 < error_rate < 1.0:
            raise ValueError("error_rate must be in (0, 1)")

        m = math.ceil(-capacity * math.log(error_rate) / (math.log(2) ** 2))
        k = max(1, round((m / capacity) * math.log(2)))

        self._m = m
        self._k = k
        self._bits = bytearray((m + 7) // 8)
        self._count = 0
        self._metadata: dict = dict(metadata or {})

    # ------------------------------------------------------------------ core

    @staticmethod
    def _to_bytes(item: str | bytes) -> bytes:
        if isinstance(item, bytes):
            return item
        return item.encode("utf-8")

    def _indices(self, item: str | bytes):
        """Kirsch–Mitzenmacher double hashing: derive k indices from 2 hashes."""
        digest = hashlib.blake2b(self._to_bytes(item), digest_size=16).digest()
        h1 = int.from_bytes(digest[:8], "little")
        h2 = int.from_bytes(digest[8:], "little")
        m = self._m
        for i in range(self._k):
            yield (h1 + i * h2) % m

    def add(self, item: str | bytes) -> None:
        """Insert `item`. Idempotent — adding the same item twice is a no-op
        at the bit level, but `len(filter)` counts insertion calls."""
        already_present = True
        for idx in self._indices(item):
            byte, bit = divmod(idx, 8)
            mask = 1 << bit
            if not self._bits[byte] & mask:
                self._bits[byte] |= mask
                already_present = False
        if not already_present:
            self._count += 1

    def __contains__(self, item: str | bytes) -> bool:
        for idx in self._indices(item):
            byte, bit = divmod(idx, 8)
            if not self._bits[byte] & (1 << bit):
                return False
        return True

    def __len__(self) -> int:
        return self._count

    # -------------------------------------------------------------- metadata

    @property
    def metadata(self) -> dict:
        return self._metadata

    @property
    def capacity_bits(self) -> int:
        return self._m

    @property
    def hash_count(self) -> int:
        return self._k

    # ---------------------------------------------------------- persistence

    def save(self, path: Path) -> None:
        """Atomically persist the filter to `path`."""
        meta_bytes = json.dumps(self._metadata, separators=(",", ":")).encode("utf-8")
        header = _HEADER_STRUCT.pack(
            _MAGIC, _FORMAT_VERSION, self._m, self._k, self._count, len(meta_bytes)
        )
        tmp = path.with_suffix(path.suffix + ".tmp")
        with open(tmp, "wb") as f:
            f.write(header)
            f.write(meta_bytes)
            f.write(self._bits)
        os.replace(tmp, path)

    @classmethod
    def load(cls, path: Path) -> BloomFilter:
        """Load a filter from `path`. Raises BloomFilterError on corruption."""
        with open(path, "rb") as f:
            raw_header = f.read(_HEADER_STRUCT.size)
            if len(raw_header) != _HEADER_STRUCT.size:
                raise BloomFilterError("file too small to contain a header")
            magic, version, m, k, count, meta_len = _HEADER_STRUCT.unpack(raw_header)
            if magic != _MAGIC:
                raise BloomFilterError(f"bad magic: {magic!r}")
            if version != _FORMAT_VERSION:
                raise BloomFilterError(f"unsupported format version: {version}")

            meta_bytes = f.read(meta_len)
            if len(meta_bytes) != meta_len:
                raise BloomFilterError("truncated metadata block")
            try:
                metadata = json.loads(meta_bytes.decode("utf-8")) if meta_len else {}
            except (json.JSONDecodeError, UnicodeDecodeError) as e:
                raise BloomFilterError(f"invalid metadata: {e}") from e

            expected_bytes = (m + 7) // 8
            bits = f.read(expected_bytes)
            if len(bits) != expected_bytes:
                raise BloomFilterError("truncated bit array")

        obj = cls.__new__(cls)
        obj._m = m
        obj._k = k
        obj._bits = bytearray(bits)
        obj._count = count
        obj._metadata = metadata
        return obj
