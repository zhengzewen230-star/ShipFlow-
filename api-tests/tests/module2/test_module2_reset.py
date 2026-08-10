import pytest

from common.module2_reset import Module2ResetGateError, validate_reset_gate, reviewed_baseline_files


def approved_environment():
    return {
        "SHIPFLOW_MODULE2_ALLOW_RESET": "1",
        "SHIPFLOW_MODULE2_RESET_DB_URL": "jdbc:mysql://isolated-db:3307/shipflow_http_test?useSSL=false",
        "SHIPFLOW_MODULE2_RESET_DB_USERNAME": "module2_reset",
        "SHIPFLOW_MODULE2_RESET_DB_PASSWORD": "runtime-only",
        "SHIPFLOW_MODULE2_RESET_DB_ACCOUNT": "module2_reset@%",
        "SHIPFLOW_MODULE2_BASE_URL": "https://module2-isolated.example",
        "SHIPFLOW_MODULE2_ISOLATED_BASE_URL": "https://module2-isolated.example",
    }


def test_reset_gate_accepts_only_exact_approved_target_and_account():
    target = validate_reset_gate(
        approved_environment(),
        selected_database="shipflow_http_test",
        current_account="module2_reset@%",
    )
    assert target.database == "shipflow_http_test"
    assert target.username == "module2_reset"


def test_reviewed_baseline_files_rejects_unreviewed_content(tmp_path):
    control = tmp_path / "database" / "http-test-control"
    control.mkdir(parents=True)
    for number in range(1, 7):
        (control / f"0{number}_baseline.sql").write_text("SELECT 1;", encoding="utf-8")
    with pytest.raises(Module2ResetGateError):
        reviewed_baseline_files(tmp_path)


@pytest.mark.parametrize(
    ("change", "database", "account"),
    [
        ({"SHIPFLOW_MODULE2_ALLOW_RESET": "true"}, "shipflow_http_test", "module2_reset@%"),
        ({"SHIPFLOW_MODULE2_RESET_DB_URL": "jdbc:mysql://isolated-db/shipflow_test"}, "shipflow_test", "module2_reset@%"),
        ({"SHIPFLOW_MODULE2_ISOLATED_BASE_URL": "https://another.example"}, "shipflow_http_test", "module2_reset@%"),
        ({"SHIPFLOW_MODULE2_RESET_DB_ACCOUNT": "module2_reset@localhost"}, "shipflow_http_test", "module2_reset@%"),
    ],
)
def test_reset_gate_rejects_any_inexact_condition(change, database, account):
    environment = approved_environment()
    environment.update(change)
    with pytest.raises(Module2ResetGateError):
        validate_reset_gate(environment, selected_database=database, current_account=account)
