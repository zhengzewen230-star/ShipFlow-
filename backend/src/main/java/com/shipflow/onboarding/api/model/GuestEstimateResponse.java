package com.shipflow.onboarding.api.model;

import java.time.OffsetDateTime;

public record GuestEstimateResponse(String referenceNo, String status, boolean formalQuote, String notice,
                                    OffsetDateTime submittedAt) {
}
