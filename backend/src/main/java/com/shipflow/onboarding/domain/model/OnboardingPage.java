package com.shipflow.onboarding.domain.model;

import java.util.List;

public record OnboardingPage(int page, int pageSize, long total, int totalPages, List<OnboardingApplication> items) {
}
