import os
import re
from pathlib import Path

import yaml

PROJECT_ROOT = Path(__file__).parent.parent
ENVIRONMENT_VARIABLE_PATTERN = re.compile(r"^\$\{([A-Z][A-Z0-9_]*)\}$")
CI_REQUIRED_SECRET_VARIABLES = (
    "SHIPFLOW_QA_DB_PASSWORD",
    "SHIPFLOW_TEST_PASSWORD",
    "SHIPFLOW_PLATFORM_TEST_PASSWORD",
)

def load_yaml(file_path):
    with open(file_path,mode="r",encoding="utf-8") as file:
        return yaml.safe_load(file)

def resolve_environment_variables(value, missing_variables=None):
    missing_variables = missing_variables if missing_variables is not None else set()

    if isinstance(value, dict):
        return {
            key: resolve_environment_variables(item, missing_variables)
            for key, item in value.items()
        }
    if isinstance(value, list):
        return [
            resolve_environment_variables(item, missing_variables)
            for item in value
        ]
    if not isinstance(value, str):
        return value

    match = ENVIRONMENT_VARIABLE_PATTERN.fullmatch(value)
    if match is None:
        return value

    variable_name = match.group(1)
    variable_value = os.getenv(variable_name)
    if not variable_value:
        missing_variables.add(variable_name)
        return value
    return variable_value

def validate_ci_environment(env_config):
    missing_variables = {
        variable_name
        for variable_name in CI_REQUIRED_SECRET_VARIABLES
        if not os.getenv(variable_name)
    }
    resolved_config = resolve_environment_variables(
        env_config,
        missing_variables,
    )
    if missing_variables:
        missing_names = ", ".join(sorted(missing_variables))
        raise RuntimeError(
            f"Missing required environment variables: {missing_names}"
        )
    return resolved_config

def load_config():
    main_config_path=PROJECT_ROOT/"config"/"config.yaml"
    main_config=load_yaml(main_config_path)

    active_env=os.getenv("SHIPFLOW_ENV", main_config["active_env"])

    env_config_path=(PROJECT_ROOT/"config"/"env"/f"{active_env}.yaml")
    env_config=load_yaml(env_config_path)
    if active_env == "ci":
        env_config = validate_ci_environment(env_config)
    return env_config
settings=load_config()
