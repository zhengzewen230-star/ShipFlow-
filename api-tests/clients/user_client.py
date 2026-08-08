class UserClient:
    def __init__(self,http_client):
        self.http_client = http_client
    def get_current_user(self,access_token=None):
        request_headers={}
        if access_token:
            request_headers["Authorization"] = f"Bearer {access_token}"
        return self.http_client.get(
            path="/api/v1/users/me",
            headers=request_headers
        )