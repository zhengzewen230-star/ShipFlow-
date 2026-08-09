package com.shipflow.user.domain.model;

import java.util.List;

public record UserPage(int page, int pageSize, long total, int totalPages, List<User> items) {}
