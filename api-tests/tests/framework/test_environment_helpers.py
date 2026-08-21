from common.environment import missing_environment_variables


def test_missing_environment_variables_only_reports_names(monkeypatch):
    monkeypatch.delenv("MODULE2_TEST_SECRET", raising=False)
    monkeypatch.setenv("MODULE2_TEST_PRESENT", "value")

    assert missing_environment_variables([
        "MODULE2_TEST_SECRET",
        "MODULE2_TEST_PRESENT",
    ]) == ["MODULE2_TEST_SECRET"]
