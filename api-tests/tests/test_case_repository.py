def test_load_ready_qa_api_test_cases(
    api_test_case_repository,
):
    test_cases = (
        api_test_case_repository.find_ready_cases(
            environment_scope="QA"
        )
    )

    assert len(test_cases) == 31

    case_numbers = [
        test_case.case_no
        for test_case in test_cases
    ]

    assert len(case_numbers) == len(set(case_numbers))

    assert all(
        test_case.enabled is True
        for test_case in test_cases
    )

    assert all(
        test_case.automation_status == "READY"
        for test_case in test_cases
    )

    assert all(
        test_case.environment_scope == "QA"
        for test_case in test_cases
    )

    execution_orders = [
        test_case.execution_order
        for test_case in test_cases
    ]

    assert execution_orders == sorted(execution_orders)

    assert all(
        isinstance(test_case.headers_template, dict)
        for test_case in test_cases
    )

    assert all(
        isinstance(test_case.cookie_template, dict)
        for test_case in test_cases
    )

    assert all(
        isinstance(test_case.assertions, list)
        for test_case in test_cases
    )

    assert all(
        isinstance(test_case.setup_steps, list)
        for test_case in test_cases
    )

    assert all(
        isinstance(test_case.extractors, list)
        for test_case in test_cases
    )

    assert all(
        isinstance(test_case.teardown_steps, list)
        for test_case in test_cases
    )

    assert all(
        isinstance(test_case.tags, list)
        for test_case in test_cases
    )