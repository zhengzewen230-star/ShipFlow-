import pytest


@pytest.mark.auth
@pytest.mark.smoke
def test_get_csrf_token(auth_client):
    response=auth_client.get_csrf_token()
    assert response.status_code == 204
    assert response.text == ""
    assert "XSRF-TOKEN" in response.cookies
    csrf_token = response.cookies["XSRF-TOKEN"]
    assert csrf_token is not None
    assert len(csrf_token) > 0