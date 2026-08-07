from time import perf_counter

import requests

from common.logger import get_logger


class HttpClient:
    def __init__(self,base_url,timeout=5,verify_ssl=False):
        self.base_url = base_url
        self.timeout = timeout
        self.verify_ssl = verify_ssl
        self.session = requests.Session()
    def request(self,method,path,**kwargs):
        logger = get_logger(__name__)
        start_time = perf_counter()
        url = self.base_url + path
        request_timeout = kwargs.pop("timeout",self.timeout)

        try:
            response = self.session.request(method=method.upper(),url=url,
                                        timeout=request_timeout,
                                        verify=self.verify_ssl,
                                        **kwargs)

            elapsed_ms = (perf_counter() - start_time) * 1000

            logger.info(
                "HTTP %s %s -> %s | %.2f ms",
                method.upper(),
                path,
                response.status_code,
                elapsed_ms
            )
            return response

        except requests.RequestException:
            elapsed_ms = (perf_counter() - start_time) * 1000

            logger.exception(
                "HTTP %s %s -> REQUEST_FAILED | %.2f ms",
                method.upper(),
                path,
                elapsed_ms
            )
            raise
    def get(self,path,**kwargs):
        return self.request("GET",path,**kwargs)
    def post(self,path,**kwargs):
        return self.request("POST",path,**kwargs)
    def clear_cookies(self):
        self.session.cookies.clear()
    def close(self):
        self.session.close()