import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.post_tracker import PostTracker


def test_mark_and_check(tmp_path: Path):
    tracker = PostTracker(tmp_path / "t.bloom", capacity=1000)
    assert not tracker.is_processed("t3_abc")
    tracker.mark_processed("t3_abc")
    assert tracker.is_processed("t3_abc")


def test_bloom_survives_reopen(tmp_path: Path):
    path = tmp_path / "t.bloom"
    tracker = PostTracker(path, capacity=1000)
    tracker.mark_processed("t3_xyz")
    tracker.flush()

    reopened = PostTracker(path, capacity=1000)
    assert reopened.is_processed("t3_xyz")


def test_corrupt_file_rebuilds_fresh(tmp_path: Path):
    path = tmp_path / "t.bloom"
    path.write_bytes(b"not a bloom filter")
    tracker = PostTracker(path, capacity=1000)
    assert not tracker.is_processed("anything")
    tracker.mark_processed("x")
    tracker.flush()

    # Reload confirms we wrote a valid file.
    reopened = PostTracker(path, capacity=1000)
    assert reopened.is_processed("x")


def test_migrate_from_state_json(tmp_path: Path):
    state_file = tmp_path / "state.json"
    legacy = {
        "version": 2,
        "processed_post_ids": {"t3_a": {}, "t3_b": {}, "t3_c": {}},
        "processed_urls": {
            "https://i.redd.it/one.png": {"status": "saved"},
            "https://i.redd.it/two.png": {"status": "not_meme"},
        },
    }
    state_file.write_text(json.dumps(legacy))

    tracker = PostTracker(tmp_path / "t.bloom", capacity=1000)
    imported = tracker.migrate_from_state_json(state_file)

    assert imported == 5
    assert tracker.is_processed("t3_a")
    assert tracker.is_processed("t3_b")
    assert tracker.is_processed("https://i.redd.it/one.png")


def test_migrate_from_missing_state_is_noop(tmp_path: Path):
    tracker = PostTracker(tmp_path / "t.bloom", capacity=1000)
    assert tracker.migrate_from_state_json(tmp_path / "missing.json") == 0


def test_len_reflects_inserts(tmp_path: Path):
    tracker = PostTracker(tmp_path / "t.bloom", capacity=1000)
    tracker.mark_processed("a")
    tracker.mark_processed("b")
    tracker.mark_processed("a")  # duplicate
    assert len(tracker) == 2


def test_content_hash_mark_and_check(tmp_path: Path):
    tracker = PostTracker(tmp_path / "t.bloom", capacity=1000)
    sha1 = "a" * 40
    assert not tracker.is_content_processed(sha1)
    tracker.mark_content_processed(sha1)
    assert tracker.is_content_processed(sha1)
    assert not tracker.is_processed(sha1)  # raw sha1 without prefix is not marked


def test_content_hash_namespace_isolation(tmp_path: Path):
    tracker = PostTracker(tmp_path / "t.bloom", capacity=1000)
    sha1 = "b" * 40
    # Marking a URL that literally starts with "sha1:" is the same key internally
    tracker.mark_processed(f"sha1:{sha1}")
    assert tracker.is_content_processed(sha1)
    # A different sha1 is not affected
    assert not tracker.is_content_processed("c" * 40)


def test_metadata_persists_across_flush(tmp_path: Path):
    bloom_path = tmp_path / "t.bloom"
    tracker = PostTracker(bloom_path, capacity=1000)
    tracker.metadata["sha1_indexed"] = True
    tracker.flush()

    reopened = PostTracker(bloom_path, capacity=1000)
    assert reopened.metadata.get("sha1_indexed") is True
