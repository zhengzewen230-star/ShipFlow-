from common.scenario_context import ScenarioContext


class TokenContext(ScenarioContext):
    def __init__(self):
        self.xsrf_token=None
        self.access_token=None
        self.refresh_token=None
    def clear(self):
        self.xsrf_token=None
        self.access_token=None
        self.refresh_token=None
