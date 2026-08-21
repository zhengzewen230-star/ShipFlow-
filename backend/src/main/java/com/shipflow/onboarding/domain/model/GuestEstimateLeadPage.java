package com.shipflow.onboarding.domain.model;

import java.util.List;

public record GuestEstimateLeadPage(int page, int pageSize, long total, int totalPages, List<GuestEstimateLead> items) {
}
