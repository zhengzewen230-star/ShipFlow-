"""第二模块冻结 66 条真实 HTTP 场景入口。

每个 node 以冻结 case_no 为参数；执行阶段由模块二运行时 fixture 提供真实会话、
CSRF、资源图和 HTTP 客户端，不能退回为旧的二十条静态样例。
"""

import pytest

from common.module2_case_catalog import MODULE2_FROZEN_CASE_NOS


@pytest.mark.parametrize("case_no", MODULE2_FROZEN_CASE_NOS, ids=MODULE2_FROZEN_CASE_NOS)
def test_module2_frozen_http_case(case_no, module2_case_executor):
    """执行一个冻结 case_no 对应的真实 HTTP 合同。"""
    module2_case_executor.execute(case_no)
