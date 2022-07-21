# coding: utf-8

from fastapi.testclient import TestClient

def test_execute(client: TestClient):
    response = client.request(
        "POST",
        "/terminal/execute",
        json="echo test",
    )
    assert response.status_code == 200
    assert response.json() == 'test'
