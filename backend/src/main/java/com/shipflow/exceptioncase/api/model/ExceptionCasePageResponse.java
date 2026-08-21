package com.shipflow.exceptioncase.api.model;
import java.util.List;
public record ExceptionCasePageResponse(int page, int pageSize, long totalPages, long total,
                                        List<ExceptionCaseResponse> items) { }
