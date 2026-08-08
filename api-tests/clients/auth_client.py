from http.cookiejar import Cookie
class AuthClient:
    def __init__(self,http_client):
        self.http_client=http_client

    def get_csrf_token(self):
        return self.http_client.get("/api/v1/auth/csrf")

    def login(self,tenant_code,username,password,xsrf_token):
        resquest_body = {
            "tenantCode": tenant_code,
            "username": username,
            "password": password,
        }
        resquest_headers = {
            "X-XSRF-Token": xsrf_token,
            "Cookie":f"XSRF-TOKEN={xsrf_token}",
        }
        return self.http_client.post(
            path=f"/api/v1/auth/login",
            json=resquest_body,
            headers=resquest_headers,
        )
    def refresh(self,xsrf_token,refresh_token):
        resquest_headers = {
            "X-XSRF-TOKEN": xsrf_token,
            "Cookie":(f"XSRF-TOKEN={xsrf_token};"
                      f"REFRESH_TOKEN={refresh_token}")
        }
        return self.http_client.post(
            path=f"/api/v1/auth/refresh",
            headers=resquest_headers
        )
    def logout(self,xsrf_token,refresh_token):
        resquest_headers = {
            "X-XSRF-TOKEN": xsrf_token,
            "Cookie":(f"XSRF-TOKEN={xsrf_token};"
                      f"REFRESH_TOKEN={refresh_token}")
        }
        return self.http_client.post(path=f"/api/v1/auth/logout",
                                     headers=resquest_headers)