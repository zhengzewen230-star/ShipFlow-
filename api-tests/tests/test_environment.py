import requests
import os

import pytest

from clients.http_client import HttpClient
# from framework.config_loader import settings


def test_backend_health(http_client):
    if os.getenv("SHIPFLOW_RUN_INTEGRATION") != "1":
        pytest.skip("Set SHIPFLOW_RUN_INTEGRATION=1 to run backend health checks")
    response = http_client.get("/actuator/health")
    assert response.status_code == 200
    response_data=response.json()
    assert response_data["status"] == "UP"
