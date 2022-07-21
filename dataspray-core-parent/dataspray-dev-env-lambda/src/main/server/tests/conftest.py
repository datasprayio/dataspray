# coding: utf-8

import pytest
import tempfile
from fastapi import FastAPI
from fastapi.testclient import TestClient
from dataspray.main import app as application
from dataspray.impl.config import setWorkingDirectory

@pytest.fixture
def app() -> FastAPI:
    application.dependency_overrides = {}

    return application


@pytest.fixture
def client(app) -> TestClient:

    return TestClient(app)

@pytest.fixture(autouse=True)
def run_around_tests():
    with tempfile.TemporaryDirectory() as workingDir:
        setWorkingDirectory(workingDir)
        yield