# coding: utf-8

from fastapi.testclient import TestClient
from dataspray.impl.config import getWorkingDirectory
from pathlib import Path

def test_execute(client: TestClient):
    response = client.request(
        "POST",
        "/terminal/execute",
        json='pwd',
    )
    assert response.status_code == 200
    contentLines = response.content.splitlines()
    assert len(contentLines) == 2
    assert contentLines[1].startswith(b'status 0')
    assert contentLines[0].endswith(Path(getWorkingDirectory()).name.encode())
