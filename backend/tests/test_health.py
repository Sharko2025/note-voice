def test_health_needs_no_token(client):
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    assert resp.text == "ok"
