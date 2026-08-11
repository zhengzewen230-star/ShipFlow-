package com.shipflow.tracking.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class TrackingStatusMapping {
    private static final Map<String, String> TARGETS = Map.of(
            "PICKED_UP", "IN_TRANSIT",
            "DEPARTED", "IN_TRANSIT",
            "IN_TRANSIT", "IN_TRANSIT",
            "DELIVERED", "DELIVERED",
            "RETURNED", "RETURNED",
            "LOST", "LOST");

    private TrackingStatusMapping() {
    }

    public static Optional<String> targetStatus(String eventCode) {
        if (eventCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TARGETS.get(eventCode.trim().toUpperCase(Locale.ROOT)));
    }

    public static Decision decide(String currentStatus, String targetStatus) {
        if (currentStatus.equals(targetStatus) || isTerminal(currentStatus)) {
            return Decision.RECORDED_ONLY;
        }
        if ((currentStatus.equals("DELIVERED") && targetStatus.equals("IN_TRANSIT"))
                || (currentStatus.equals("IN_TRANSIT") && targetStatus.equals("IN_TRANSIT"))) {
            return Decision.RECORDED_ONLY;
        }
        boolean allowed = switch (currentStatus) {
            case "INBOUND", "PENDING_PRICE_CONFIRMATION", "READY_FOR_OUTBOUND" -> targetStatus.equals("RETURNED");
            case "OUTBOUND" -> targetStatus.equals("IN_TRANSIT") || targetStatus.equals("RETURNED") || targetStatus.equals("LOST");
            case "IN_TRANSIT" -> targetStatus.equals("DELIVERED") || targetStatus.equals("RETURNED") || targetStatus.equals("LOST");
            default -> false;
        };
        return allowed ? Decision.ADVANCE : Decision.ILLEGAL;
    }

    private static boolean isTerminal(String status) {
        return status.equals("DELIVERED") || status.equals("RETURNED") || status.equals("LOST") || status.equals("CANCELLED");
    }

    public enum Decision {
        ADVANCE, RECORDED_ONLY, ILLEGAL
    }
}
