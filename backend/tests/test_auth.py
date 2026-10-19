import pytest

ZERO = "00000000-0000-0000-0000-000000000000"
PROTECTED = [
    ("get", "/api/v1/tasks"),
    ("post", "/api/v1/tasks"),
    ("post", "/api/v1/voice"),
    ("patch", f"/api/v1/tasks/{ZERO}"),
    ("delete", f"/api/v1/tasks/{ZERO}"),
    ("post", f"/api/v1/tasks/{ZERO}/restore"),
    ("post", f"/api/v1/voice/{ZERO}/undo"),
]


@pytest.mark.parametrize("method,path", PROTECTED)
def test_missing_token_rejected(client, method, path):
    resp = getattr(client, method)(path)
    assert resp.status_code == 401
    assert resp.json()["detail"] == "Token không hợp lệ"


@pytest.mark.parametrize("method,path", PROTECTED)
def test_wrong_token_rejected(client, method, path):
    resp = getattr(client, method)(path, headers={"Authorization": "Bearer wrong"})
    assert resp.status_code == 401


def test_non_bearer_scheme_rejected(client, auth):
    token = auth["Authorization"].removeprefix("Bearer ")
    resp = client.get("/api/v1/tasks", headers={"Authorization": f"Basic {token}"})
    assert resp.status_code == 401


def test_valid_token_accepted(client, auth):
    assert client.get("/api/v1/tasks", headers=auth).status_code == 200
