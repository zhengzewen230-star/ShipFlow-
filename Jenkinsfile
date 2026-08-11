pipeline {
    agent {
        label 'windows'
    }

    options {
        skipDefaultCheckout(true)
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '40'))
        disableConcurrentBuilds()
        timestamps()
    }

    parameters {
        string(
            name: 'API_BASE_URL',
            defaultValue: 'http://localhost:8080',
            description: 'Externally managed ShipFlow backend base URL'
        )
        string(
            name: 'QA_DB_HOST',
            defaultValue: '172.29.128.47',
            description: 'Externally managed shipflow_qa MySQL host'
        )
        string(
            name: 'QA_DB_PORT',
            defaultValue: '3307',
            description: 'Externally managed shipflow_qa MySQL port'
        )
        string(
            name: 'QA_DB_NAME',
            defaultValue: 'shipflow_qa',
            description: 'QA test-case database name'
        )
        string(
            name: 'QA_DB_USERNAME',
            defaultValue: 'shipflow_qa_reader',
            description: 'Read-only QA test-case database username'
        )
        booleanParam(
            name: 'RUN_MODULE2_ISOLATED',
            defaultValue: false,
            description: 'Run only the isolated module-2 66-case HTTP acceptance suite'
        )
        string(
            name: 'MODULE2_BASE_URL',
            defaultValue: 'http://localhost:18080',
            description: 'Externally managed isolated module-2 backend base URL'
        )
        string(
            name: 'MODULE2_HTTP_TEST_DB_USERNAME',
            defaultValue: '',
            description: 'Dedicated shipflow_http_test database username; required only for module 2'
        )
    }

    environment {
        SHIPFLOW_ENV = 'ci'
        SHIPFLOW_API_BASE_URL = "${params.API_BASE_URL}"
        SHIPFLOW_QA_DB_HOST = "${params.QA_DB_HOST}"
        SHIPFLOW_QA_DB_PORT = "${params.QA_DB_PORT}"
        SHIPFLOW_QA_DB_NAME = "${params.QA_DB_NAME}"
        SHIPFLOW_QA_DB_USERNAME = "${params.QA_DB_USERNAME}"
        PYTHONDONTWRITEBYTECODE = '1'
        PYTHONUNBUFFERED = '1'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare Python') {
            steps {
                dir('api-tests') {
                    powershell '''
$basePython = 'D:\\python\\python.exe'
$venvPath = [System.IO.Path]::GetFullPath((Join-Path $PWD '.venv'))
$apiTestsPath = [System.IO.Path]::GetFullPath($PWD.Path)
$expectedPrefix = $apiTestsPath.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar
) + [System.IO.Path]::DirectorySeparatorChar

if (-not $venvPath.StartsWith(
    $expectedPrefix,
    [System.StringComparison]::OrdinalIgnoreCase
)) {
    throw 'Refusing to manage a virtual environment outside api-tests'
}

if (-not (Test-Path -LiteralPath $basePython -PathType Leaf)) {
    throw 'Configured base Python executable was not found'
}

& $basePython --version
if ($LASTEXITCODE -ne 0) {
    throw 'Configured base Python executable could not run'
}

$venvPython = Join-Path $venvPath 'Scripts\\python.exe'
$venvConfig = Join-Path $venvPath 'pyvenv.cfg'
$venvIsValid = (
    (Test-Path -LiteralPath $venvPython -PathType Leaf) -and
    (Test-Path -LiteralPath $venvConfig -PathType Leaf)
)

if ($venvIsValid) {
    & $venvPython -c `
        'import os, sys; raise SystemExit(0 if sys.prefix != sys.base_prefix and os.path.samefile(sys._base_executable, sys.argv[1]) else 1)' `
        $basePython
    $venvIsValid = $LASTEXITCODE -eq 0
}

if (-not $venvIsValid) {
    if (Test-Path -LiteralPath $venvPath) {
        Remove-Item -LiteralPath $venvPath -Recurse -Force
    }
    & $basePython -m venv .venv
    if ($LASTEXITCODE -ne 0) {
        throw 'Python virtual environment creation failed'
    }
}

& $venvPython --version
if ($LASTEXITCODE -ne 0) {
    throw 'Virtual environment Python could not run'
}

& $venvPython -m pip install `
    --disable-pip-version-check `
    -r requirements.txt
if ($LASTEXITCODE -ne 0) {
    throw 'Python dependency installation failed'
}
'''
                }
            }
        }

        stage('Check External Environment') {
            when {
                expression { !params.RUN_MODULE2_ISOLATED }
            }
            environment {
                SHIPFLOW_QA_DB_PASSWORD = credentials('shipflow-qa-db-password')
                SHIPFLOW_TEST_PASSWORD = credentials('shipflow-test-password')
                SHIPFLOW_PLATFORM_TEST_PASSWORD = credentials('shipflow-platform-test-password')
            }
            steps {
                dir('api-tests') {
                    powershell '''
$checkScript = @'
import os
import sys

import pymysql
import requests


def fail(message):
    print(message, file=sys.stderr)
    raise SystemExit(1)


try:
    response = requests.get(
        os.environ["SHIPFLOW_API_BASE_URL"].rstrip("/") + "/actuator/health",
        timeout=10,
    )
    payload = response.json()
    if response.status_code != 200 or payload.get("status") != "UP":
        fail("Backend health check failed")
except Exception:
    fail("Backend health check failed")

print("Backend health check: PASS")

try:
    connection = pymysql.connect(
        host=os.environ["SHIPFLOW_QA_DB_HOST"],
        port=int(os.environ["SHIPFLOW_QA_DB_PORT"]),
        user=os.environ["SHIPFLOW_QA_DB_USERNAME"],
        password=os.environ["SHIPFLOW_QA_DB_PASSWORD"],
        database=os.environ["SHIPFLOW_QA_DB_NAME"],
        charset="utf8mb4",
        connect_timeout=10,
        autocommit=True,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT COUNT(*) FROM api_test_case "
                "WHERE enabled = 1 AND automation_status = 'READY'"
            )
            row = cursor.fetchone()
            if row is None or row[0] < 1:
                fail("QA database read-only connectivity check failed")
    finally:
        connection.close()
except Exception:
    fail("QA database read-only connectivity check failed")

print("QA database read-only connectivity check: PASS")
'@

$checkScript | & .\\.venv\\Scripts\\python.exe -
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
'''
                }
            }
        }

        stage('Run API Tests') {
            when {
                expression { !params.RUN_MODULE2_ISOLATED }
            }
            environment {
                SHIPFLOW_QA_DB_PASSWORD = credentials('shipflow-qa-db-password')
                SHIPFLOW_TEST_PASSWORD = credentials('shipflow-test-password')
                SHIPFLOW_PLATFORM_TEST_PASSWORD = credentials('shipflow-platform-test-password')
            }
            steps {
                dir('api-tests') {
                    powershell '''
New-Item -ItemType Directory -Force -Path reports | Out-Null
& .\\.venv\\Scripts\\python.exe -m pytest -v `
    --alluredir=reports/allure-results `
    --clean-alluredir `
    --junitxml=reports/junit.xml
exit $LASTEXITCODE
'''
                }
            }
        }

        stage('Check Module 2 Isolated Environment') {
            when {
                expression { params.RUN_MODULE2_ISOLATED }
            }
            environment {
                SHIPFLOW_MODULE2_RUN = '1'
                SHIPFLOW_MODULE2_ISOLATED_ENV = '1'
                SHIPFLOW_MODULE2_BASE_URL = "${params.MODULE2_BASE_URL}"
                SHIPFLOW_MODULE2_ISOLATED_BASE_URL = "${params.MODULE2_BASE_URL}"
                SHIPFLOW_MODULE2_BUSINESS_DB_NAME = 'shipflow_http_test'
                SHIPFLOW_QA_DB_URL = credentials('shipflow-module2-qa-db-url')
                SHIPFLOW_QA_DB_PASSWORD = credentials('shipflow-qa-db-password')
                SHIPFLOW_HTTP_TEST_DB_URL = credentials('shipflow-module2-http-test-db-url')
                SHIPFLOW_HTTP_TEST_DB_USERNAME = "${params.MODULE2_HTTP_TEST_DB_USERNAME}"
                SHIPFLOW_HTTP_TEST_DB_PASSWORD = credentials('shipflow-module2-http-test-db-password')
                SHIPFLOW_PLATFORM_TEST_PASSWORD = credentials('shipflow-platform-test-password')
            }
            steps {
                dir('api-tests') {
                    powershell '''
$checkScript = @'
import os
import sys
from urllib.parse import urlparse

import pymysql
import requests


def fail(message):
    print(message, file=sys.stderr)
    raise SystemExit(1)


def verify_database(url_name, username_name, password_name, expected_database):
    raw_url = os.environ[url_name].removeprefix("jdbc:")
    parsed = urlparse(raw_url)
    database = parsed.path.lstrip("/").split("?", 1)[0]
    if parsed.scheme != "mysql" or not parsed.hostname or database != expected_database:
        fail(f"{url_name} target validation failed")
    try:
        connection = pymysql.connect(
            host=parsed.hostname,
            port=parsed.port or 3306,
            user=os.environ[username_name],
            password=os.environ[password_name],
            database=database,
            connect_timeout=10,
            autocommit=True,
        )
        try:
            with connection.cursor() as cursor:
                cursor.execute("SELECT DATABASE()")
                if cursor.fetchone()[0] != expected_database:
                    fail(f"{url_name} selected database validation failed")
        finally:
            connection.close()
    except Exception:
        fail(f"{url_name} connectivity validation failed")


base_url = os.environ["SHIPFLOW_MODULE2_BASE_URL"].rstrip("/")
if base_url != os.environ["SHIPFLOW_MODULE2_ISOLATED_BASE_URL"].rstrip("/"):
    fail("Module 2 API URL approval validation failed")
try:
    response = requests.get(base_url + "/actuator/health", timeout=10)
    if response.status_code != 200 or response.json().get("status") != "UP":
        fail("Module 2 backend health check failed")
except Exception:
    fail("Module 2 backend health check failed")

if not os.environ["SHIPFLOW_HTTP_TEST_DB_USERNAME"]:
    fail("MODULE2_HTTP_TEST_DB_USERNAME is required")
verify_database("SHIPFLOW_QA_DB_URL", "SHIPFLOW_QA_DB_USERNAME", "SHIPFLOW_QA_DB_PASSWORD", "shipflow_qa")
verify_database("SHIPFLOW_HTTP_TEST_DB_URL", "SHIPFLOW_HTTP_TEST_DB_USERNAME", "SHIPFLOW_HTTP_TEST_DB_PASSWORD", "shipflow_http_test")
print("Module 2 isolated environment check: PASS")
'@

$checkScript | & .\\.venv\\Scripts\\python.exe -
exit $LASTEXITCODE
'''
                }
            }
        }

        stage('Run Module 2 Isolated API Tests') {
            when {
                expression { params.RUN_MODULE2_ISOLATED }
            }
            environment {
                SHIPFLOW_MODULE2_RUN = '1'
                SHIPFLOW_MODULE2_ISOLATED_ENV = '1'
                SHIPFLOW_MODULE2_BASE_URL = "${params.MODULE2_BASE_URL}"
                SHIPFLOW_MODULE2_ISOLATED_BASE_URL = "${params.MODULE2_BASE_URL}"
                SHIPFLOW_MODULE2_BUSINESS_DB_NAME = 'shipflow_http_test'
                SHIPFLOW_QA_DB_URL = credentials('shipflow-module2-qa-db-url')
                SHIPFLOW_QA_DB_PASSWORD = credentials('shipflow-qa-db-password')
                SHIPFLOW_HTTP_TEST_DB_URL = credentials('shipflow-module2-http-test-db-url')
                SHIPFLOW_HTTP_TEST_DB_USERNAME = "${params.MODULE2_HTTP_TEST_DB_USERNAME}"
                SHIPFLOW_HTTP_TEST_DB_PASSWORD = credentials('shipflow-module2-http-test-db-password')
                SHIPFLOW_PLATFORM_TEST_PASSWORD = credentials('shipflow-platform-test-password')
            }
            steps {
                dir('api-tests') {
                    powershell '''
New-Item -ItemType Directory -Force -Path reports | Out-Null
& .\\.venv\\Scripts\\python.exe -m pytest -q tests/module2/test_module2_api.py `
    --alluredir=reports/module2-allure-results `
    --clean-alluredir `
    --junitxml=reports/module2-junit.xml
exit $LASTEXITCODE
'''
                }
            }
        }
    }

    post {
        always {
            powershell '''
New-Item -ItemType Directory -Force -Path api-tests/reports/allure-results | Out-Null
New-Item -ItemType Directory -Force -Path api-tests/reports/module2-allure-results | Out-Null
'''
            allure(
                includeProperties: false,
                jdk: '',
                results: [
                    [path: 'api-tests/reports/allure-results'],
                    [path: 'api-tests/reports/module2-allure-results']
                ]
            )
            junit(
                allowEmptyResults: true,
                testResults: 'api-tests/reports/junit.xml,api-tests/reports/module2-junit.xml'
            )
            archiveArtifacts(
                allowEmptyArchive: true,
                artifacts: 'api-tests/reports/junit.xml,api-tests/reports/module2-junit.xml',
                fingerprint: false
            )
        }
        cleanup {
            powershell '''
Remove-Item -LiteralPath api-tests/.pytest_cache -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath api-tests/__pycache__ -Recurse -Force -ErrorAction SilentlyContinue
'''
        }
    }
}
