"""End-to-end with real audio recorded on the phone. Files live in ../samples (not committed).

recorded_at is fixed to Monday 19/10/2026 14:30 (Vietnam) so relative dates are predictable.
"""
import uuid
from pathlib import Path

import pytest

from app.config import get_settings

pytestmark = pytest.mark.openai

SAMPLES = Path(__file__).resolve().parents[1] / "samples"
RECORDED_AT = "2026-10-19T14:30:00+07:00"
# file -> list of (keyword in title, due_date, starred); see docs/plans/phase-1.md Task 6
EXPECTED = {
    "goi-anh-tuan.m4a": [("tuấn", "2026-10-20", False)],
    "uong-thuoc.m4a": [("thuốc", "2026-10-19", False)],
    "bao-cao-thu-6.m4a": [("báo cáo", "2026-10-23", True)],
    "hai-viec.m4a": [("sữa", None, False), ("lan", "2026-10-20", False)],
    "sieu-thi-chu-nhat.m4a": [("siêu thị", "2026-10-25", False)],
    "call-bank.m4a": [("bank", "2026-10-20", False)],
    "milk-rent.m4a": [("milk", None, None), ("rent", "2026-10-23", True)],
}


@pytest.fixture
def real_client(app):
    from fastapi.testclient import TestClient

    return TestClient(app)


def send(client, path):
    return client.post(
        "/api/v1/voice",
        headers={"Authorization": f"Bearer {get_settings().device_token}"},
        data={"entry_id": str(uuid.uuid4()), "recorded_at": RECORDED_AT},
        files={"file": (path.name, path.read_bytes(), "audio/mp4")},
    )


@pytest.mark.parametrize("name,expected", EXPECTED.items())
def test_real_audio(real_client, name, expected):
    path = SAMPLES / name
    if not path.exists():
        pytest.skip(f"missing sample {name}")
    resp = send(real_client, path)
    assert resp.status_code == 200, resp.text
    tasks = resp.json()["tasks"]
    assert len(tasks) == len(expected), tasks
    for keyword, due, starred in expected:
        [task] = [t for t in tasks if keyword in t["title"].lower()] or [None]
        assert task is not None, f"no title with {keyword!r}: {tasks}"
        assert task["due_date"] == due, task
        if starred is not None:
            assert task["starred"] is starred, task


def test_real_audio_without_task(real_client):
    path = SAMPLES / "troi-dep.m4a"
    if not path.exists():
        pytest.skip("missing sample troi-dep.m4a")
    resp = send(real_client, path)
    assert resp.status_code == 422
    assert resp.json()["raw_text"]
