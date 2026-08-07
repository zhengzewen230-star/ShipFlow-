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
                    bat '''@echo off
if not exist ".venv/Scripts/python.exe" (
    py -3.12 -m venv .venv
)
".venv/Scripts/python.exe" -m pip install --disable-pip-version-check -r requirements.txt
'''
                }
            }
        }

        stage('Check External Environment') {
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

$checkScript | & ./.venv/Scripts/python.exe -
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
'''
                }
            }
        }

        stage('Run API Tests') {
            environment {
                SHIPFLOW_QA_DB_PASSWORD = credentials('shipflow-qa-db-password')
                SHIPFLOW_TEST_PASSWORD = credentials('shipflow-test-password')
                SHIPFLOW_PLATFORM_TEST_PASSWORD = credentials('shipflow-platform-test-password')
            }
            steps {
                dir('api-tests') {
                    powershell '''
New-Item -ItemType Directory -Force -Path reports | Out-Null
& ./.venv/Scripts/python.exe -m pytest -v `
    --alluredir=reports/allure-results `
    --clean-alluredir `
    --junitxml=reports/junit.xml
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
'''
            allure(
                includeProperties: false,
                jdk: '',
                results: [[path: 'api-tests/reports/allure-results']]
            )
            junit(
                allowEmptyResults: true,
                testResults: 'api-tests/reports/junit.xml'
            )
            archiveArtifacts(
                allowEmptyArchive: true,
                artifacts: 'api-tests/reports/junit.xml',
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
