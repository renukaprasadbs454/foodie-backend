package com.foodie.admin.dto.response;

public record AdminReviewStatsDto(
        long totalReviews,
        long totalComplaints,
        long auditLogs,
        long resolvedIssues
) {
}
