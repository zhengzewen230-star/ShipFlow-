"""第二模块冻结的 66 条用例清单。

执行器必须按此清单选择用例，不能以 execution_order 的连续范围替代。
"""

MODULE2_FROZEN_CASE_NOS = (
    *(f"TENANT-{number:03d}" for number in range(1, 18)),
    *(f"STORE-{number:03d}" for number in range(1, 18)),
    *(f"USER-{number:03d}" for number in range(1, 18)),
    "USER-020",
    *(f"RBAC-{number:03d}" for number in range(1, 15)),
)

if len(MODULE2_FROZEN_CASE_NOS) != 66 or len(set(MODULE2_FROZEN_CASE_NOS)) != 66:
    raise RuntimeError("第二模块冻结用例清单必须精确包含 66 个唯一 case_no")
