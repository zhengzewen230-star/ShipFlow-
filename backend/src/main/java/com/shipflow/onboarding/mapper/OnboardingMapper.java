package com.shipflow.onboarding.mapper;

import com.shipflow.onboarding.domain.model.OnboardingApplication;
import com.shipflow.onboarding.domain.model.GuestEstimateLead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OnboardingMapper {
    int insertEstimate(@Param("referenceNo") String referenceNo, @Param("requestHash") String requestHash,
                       @Param("originCountry") String originCountry, @Param("destinationCountry") String destinationCountry,
                       @Param("transportMode") String transportMode, @Param("cargoType") String cargoType, @Param("cargoName") String cargoName,
                       @Param("weight") java.math.BigDecimal weight, @Param("volume") java.math.BigDecimal volume,
                       @Param("contactName") String contactName, @Param("businessEmail") String businessEmail,
                       @Param("contactPhone") String contactPhone, @Param("idempotencyKey") String idempotencyKey);
    IdempotentEstimate findEstimateByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
    long countEstimateLeads(@Param("status") String status, @Param("keyword") String keyword,
                            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    List<GuestEstimateLead> findEstimateLeads(@Param("status") String status, @Param("keyword") String keyword,
                                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                              @Param("offset") int offset, @Param("pageSize") int pageSize);
    GuestEstimateLead findEstimateLead(@Param("leadId") Long leadId);
    int updateEstimateLeadStatus(@Param("leadId") Long leadId, @Param("status") String status,
                                 @Param("handlingRemark") String handlingRemark, @Param("handledByUserId") Long handledByUserId,
                                 @Param("handledAt") LocalDateTime handledAt, @Param("version") long version);
    int insertEstimateLeadAudit(@Param("operatorUserId") Long operatorUserId, @Param("leadId") Long leadId,
                                @Param("requestId") String requestId, @Param("reason") String reason,
                                @Param("occurredAt") LocalDateTime occurredAt);
    int insertApplication(@Param("applicationNo") String applicationNo, @Param("companyName") String companyName,
                          @Param("contactName") String contactName, @Param("businessEmail") String businessEmail,
                          @Param("contactPhone") String contactPhone, @Param("countryCode") String countryCode,
                          @Param("requestHash") String requestHash, @Param("idempotencyKey") String idempotencyKey);
    OnboardingApplication findApplication(@Param("applicationId") Long applicationId);
    OnboardingApplication findApplicationByNo(@Param("applicationNo") String applicationNo);
    OnboardingApplication findApplicationByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
    long countApplications(@Param("status") String status);
    List<OnboardingApplication> findApplications(@Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);
    int approve(@Param("applicationId") Long applicationId, @Param("version") long version, @Param("reviewRemark") String reviewRemark,
                @Param("tenantId") Long tenantId, @Param("initialUserId") Long initialUserId, @Param("reviewedBy") Long reviewedBy,
                @Param("reviewedAt") LocalDateTime reviewedAt);
    int reject(@Param("applicationId") Long applicationId, @Param("version") long version, @Param("reviewRemark") String reviewRemark,
               @Param("reviewedBy") Long reviewedBy, @Param("reviewedAt") LocalDateTime reviewedAt);
    int insertInvitation(@Param("applicationId") Long applicationId, @Param("tenantId") Long tenantId, @Param("userId") Long userId,
                         @Param("tokenHash") String tokenHash, @Param("expiresAt") LocalDateTime expiresAt);
    Invitation findInvitationByTokenHash(@Param("tokenHash") String tokenHash);
    int markInvitationUsed(@Param("invitationId") Long invitationId, @Param("now") LocalDateTime now);
    int activateUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("passwordHash") String passwordHash);

    record Invitation(Long id, Long tenantId, Long userId, String tenantCode, String username, LocalDateTime expiresAt, LocalDateTime usedAt) {}
    record IdempotentEstimate(String referenceNo, String requestHash) {}
}
