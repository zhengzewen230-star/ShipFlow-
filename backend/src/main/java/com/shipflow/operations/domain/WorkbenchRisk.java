package com.shipflow.operations.domain;

import java.time.LocalDateTime;

public record WorkbenchRisk(String id, String type, String level, String title,
                            String resourceType, Long resourceId, Long storeId,
                            LocalDateTime occurredAt, String description) {
}
