import json
import random
import string
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.bloom import BloomFilter, BloomFilterError, _HEADER_STRUCT, _MAGIC


def _rand_str(n: int = 16) -> str:
    return "".join(random.choices(string.ascii_letters + string.digits, k=n))


def test_add_and_contains():
    bf = BloomFilter(capacity=1000, error_rate=1e-3)
    items = [_rand_str() for _ in range(100)]
    for item in items:
        bf.add(item)
    for item in items:
        assert item in bf


def test_no_false_negatives_ever():
    bf = BloomFilter(capacity=1000, error_rate=0.01)
    items = [f"item-{i}" for i in range(500)]
    for item in items:
        bf.add(item)
    for item in items:
        assert item in bf, f"false negative for {item}"


def test_accepts_bytes_and_str_interchangeably():
    bf = BloomFilter(capacity=100)
    bf.add("hello")
    assert b"hello" in bf
    bf.add(b"world")
    assert "world" in bf


def test_empirical_false_positive_rate_within_tolerance():
    target_rate = 0.01
    capacity = 5_000
    bf = BloomFilter(capacity=capacity, error_rate=target_rate)

    inserted = {f"in-{i}" for i in range(capacity)}
    for item in inserted:
        bf.add(item)

    probes = [f"out-{i}" for i in range(10_000)]
    false_positives = sum(1 for p in probes if p in bf)
    observed_rate = false_positives / len(probes)

    assert observed_rate < target_rate * 2, (
        f"observed FP rate {observed_rate:.4f} exceeds 2x target {target_rate}"
    )


def test_count_tracks_unique_insertions():
    bf = BloomFilter(capacity=1000)
    bf.add("a")
    bf.add("b")
    bf.add("a")  # duplicate — bits already set
    assert len(bf) == 2


def test_save_load_round_trip(tmp_path: Path):
    bf = BloomFilter(capacity=500, error_rate=1e-3, metadata={"cursor": "t3_abc"})
    items = [f"item-{i}" for i in range(200)]
    for item in items:
        bf.add(item)

    path = tmp_path / "test.bloom"
    bf.save(path)

    loaded = BloomFilter.load(path)
    for item in items:
        assert item in loaded
    assert len(loaded) == len(bf)
    assert loaded.metadata == {"cursor": "t3_abc"}
    assert loaded.capacity_bits == bf.capacity_bits
    assert loaded.hash_count == bf.hash_count


def test_load_rejects_bad_magic(tmp_path: Path):
    path = tmp_path / "bad.bloom"
    path.write_bytes(b"XXXX" + b"\x00" * 100)
    with pytest.raises(BloomFilterError, match="bad magic"):
        BloomFilter.load(path)


def test_load_rejects_unsupported_version(tmp_path: Path):
    path = tmp_path / "bad.bloom"
    header = _HEADER_STRUCT.pack(_MAGIC, 999, 64, 3, 0, 0)
    path.write_bytes(header + b"\x00" * 8)
    with pytest.raises(BloomFilterError, match="unsupported format version"):
        BloomFilter.load(path)


def test_load_rejects_truncated_bit_array(tmp_path: Path):
    bf = BloomFilter(capacity=100)
    bf.add("x")
    path = tmp_path / "truncated.bloom"
    bf.save(path)

    data = path.read_bytes()
    path.write_bytes(data[: len(data) - 10])
    with pytest.raises(BloomFilterError, match="truncated bit array"):
        BloomFilter.load(path)


def test_save_is_atomic(tmp_path: Path):
    path = tmp_path / "atomic.bloom"
    bf1 = BloomFilter(capacity=100)
    bf1.add("first")
    bf1.save(path)

    # Orphan .tmp file from a hypothetical previous crash must not break the next save.
    (path.with_suffix(path.suffix + ".tmp")).write_bytes(b"garbage")

    bf2 = BloomFilter(capacity=100)
    bf2.add("second")
    bf2.save(path)

    loaded = BloomFilter.load(path)
    assert "second" in loaded
    assert "first" not in loaded  # overwritten by the second save


def test_rejects_invalid_construction_params():
    with pytest.raises(ValueError):
        BloomFilter(capacity=0)
    with pytest.raises(ValueError):
        BloomFilter(capacity=100, error_rate=0)
    with pytest.raises(ValueError):
        BloomFilter(capacity=100, error_rate=1.0)


def test_sizing_is_reasonable():
    bf = BloomFilter(capacity=200_000, error_rate=1e-3)
    # Expect ~2.87M bits, k≈10. Allow generous slack.
    assert 2_000_000 < bf.capacity_bits < 4_000_000
    assert 7 <= bf.hash_count <= 15
