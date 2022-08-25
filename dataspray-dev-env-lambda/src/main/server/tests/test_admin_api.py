# coding: utf-8

from fastapi.testclient import TestClient

def test_ping(client: TestClient):
    response = client.request(
        "GET",
        "/ping",
    )
    assert response.status_code == 200
