package com.shipflow.onboarding.application;

import com.shipflow.onboarding.api.model.*;
import com.shipflow.onboarding.domain.model.*;
import com.shipflow.onboarding.mapper.OnboardingMapper;
import com.shipflow.tenant.domain.model.Tenant;
import com.shipflow.tenant.mapper.TenantAuditMapper;
import com.shipflow.tenant.mapper.TenantMapper;
import com.shipflow.tenant.mapper.TenantProvisioningMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@Profile("!test")
public class OnboardingApplicationService {
    private static final List<String> INITIAL_ADMIN_PERMISSIONS = List.of("store:create", "store:read", "store:manage", "user:read", "user:manage", "role:read", "role:manage", "permission:read", "logistics:read", "quote:read", "quote:create", "quote:validate", "order:read", "order:create", "order:manage", "order:price-confirm", "tracking:read", "exception:read", "exception:manage", "billing:read", "operations:read", "audit:read");
    private final OnboardingMapper mapper; private final TenantMapper tenantMapper; private final TenantProvisioningMapper provisioning;
    private final TenantAuditMapper tenantAudit; private final PasswordEncoder passwordEncoder; private final Clock clock; private final SecureRandom random = new SecureRandom();
    public OnboardingApplicationService(OnboardingMapper mapper, TenantMapper tenantMapper, TenantProvisioningMapper provisioning,
                                        TenantAuditMapper tenantAudit, PasswordEncoder passwordEncoder, Clock clock) {
        this.mapper=mapper; this.tenantMapper=tenantMapper; this.provisioning=provisioning; this.tenantAudit=tenantAudit; this.passwordEncoder=passwordEncoder; this.clock=clock;
    }
    @Transactional
    public GuestEstimateResponse submitEstimate(CreateGuestEstimateRequest r, String key) {
        if (r.originCountry().equals(r.destinationCountry())) throw error("ONBOARDING-1005", 422);
        requireKey(key); String hash=digest(r.originCountry()+"\n"+r.destinationCountry()+"\n"+r.transportMode()+"\n"+r.cargoType()+"\n"+r.cargoName()+"\n"+r.weight()+"\n"+r.volume()+"\n"+r.contactName()+"\n"+r.businessEmail()+"\n"+r.contactPhone());
        OnboardingMapper.IdempotentEstimate prior=mapper.findEstimateByIdempotencyKey(key);
        if(prior!=null){ if(!hash.equals(prior.requestHash())) throw error("COMMON-1009",409); return estimateResponse(prior.referenceNo()); }
        String reference="EST-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        try { mapper.insertEstimate(reference,hash,r.originCountry(),r.destinationCountry(),r.transportMode(),r.cargoType().name(),r.cargoName(),r.weight(),r.volume(),r.contactName(),r.businessEmail(),r.contactPhone(),key); return estimateResponse(reference); }
        catch (DuplicateKeyException duplicate) { OnboardingMapper.IdempotentEstimate same=mapper.findEstimateByIdempotencyKey(key); if(same!=null&&hash.equals(same.requestHash())) return estimateResponse(same.referenceNo()); throw error("COMMON-1009",409); }
    }
    public GuestEstimateLeadPage listEstimateLeads(String status, String keyword, OffsetDateTime from, OffsetDateTime to, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw error("COMMON-1001", 400);
        GuestEstimateLeadStatus parsedStatus = parseLeadStatus(status);
        LocalDateTime fromUtc = utc(from);
        LocalDateTime toUtc = utc(to);
        if (fromUtc != null && toUtc != null && fromUtc.isAfter(toUtc)) throw error("COMMON-1001", 400);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 128) throw error("COMMON-1001", 400);
        long total = mapper.countEstimateLeads(parsedStatus == null ? null : parsedStatus.name(), normalizedKeyword, fromUtc, toUtc);
        return new GuestEstimateLeadPage(page, pageSize, total, (int) ((total + pageSize - 1) / pageSize),
                mapper.findEstimateLeads(parsedStatus == null ? null : parsedStatus.name(), normalizedKeyword, fromUtc, toUtc, (page - 1) * pageSize, pageSize));
    }
    public GuestEstimateLead getEstimateLead(Long leadId) { return requiredEstimateLead(leadId); }
    @Transactional
    public GuestEstimateLead updateEstimateLeadStatus(Long leadId, UpdateGuestEstimateLeadStatusRequest request, Long operatorUserId, String requestId) {
        GuestEstimateLead current = requiredEstimateLead(leadId);
        GuestEstimateLeadStatus from = parseLeadStatus(current.status());
        if (!canTransition(from, request.status())) throw error("ONBOARDING-1006", 409);
        LocalDateTime now = LocalDateTime.now(clock);
        if (mapper.updateEstimateLeadStatus(leadId, request.status().name(), blank(request.handlingRemark()), operatorUserId, now, request.version()) != 1) {
            if (mapper.findEstimateLead(leadId) == null) throw error("COMMON-1006", 404);
            throw error("COMMON-1005", 409);
        }
        mapper.insertEstimateLeadAudit(operatorUserId, leadId, requestId, from + " -> " + request.status(), now);
        return requiredEstimateLead(leadId);
    }
    @Transactional
    public OnboardingApplication apply(CreateOnboardingApplicationRequest r, String key) {
        requireKey(key); String hash=digest(r.companyName()+"\n"+r.contactName()+"\n"+r.businessEmail()+"\n"+r.contactPhone()+"\n"+r.countryCode());
        OnboardingApplication prior=mapper.findApplicationByIdempotencyKey(key);
        if(prior!=null){ if(!hash.equals(applicationHash(prior))) throw error("COMMON-1009",409); return prior; }
        String no="APP-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        try { mapper.insertApplication(no,r.companyName(),r.contactName(),r.businessEmail(),r.contactPhone(),r.countryCode(),hash,key); OnboardingApplication created=mapper.findApplicationByNo(no); if(created==null) throw new IllegalStateException("Application was not created"); return created; }
        catch (DuplicateKeyException duplicate) { OnboardingApplication same=mapper.findApplicationByIdempotencyKey(key); if(same!=null&&hash.equals(applicationHash(same))) return same; throw error("COMMON-1009",409); }
    }
    public OnboardingPage list(String status,int page,int pageSize) { if(page<1||pageSize<1||pageSize>100) throw error("COMMON-1001",400); long total=mapper.countApplications(blank(status)); return new OnboardingPage(page,pageSize,total,(int)((total+pageSize-1)/pageSize),mapper.findApplications(blank(status),(page-1)*pageSize,pageSize)); }
    public OnboardingApplication get(Long id) { return required(id); }
    @Transactional
    public ApprovalResult approve(Long id, ApproveOnboardingApplicationRequest r, Long reviewerId, String requestId) {
        OnboardingApplication app=required(id); if(!"PENDING".equals(app.status())) throw error("ONBOARDING-1001",409); if(app.version()!=r.version()) throw error("COMMON-1005",409); if(tenantMapper.findByCode(r.tenantCode())!=null) throw error("TENANT-1001",409);
        Tenant tenant=new Tenant(null,r.tenantCode(),app.companyName(),"ACTIVE",0,null,null); tenantMapper.insertTenant(tenant); Tenant created=tenantMapper.findByCode(r.tenantCode()); if(created==null) throw new IllegalStateException("Tenant was not created");
        String bootstrapPassword=token(); provisioning.insertAdminUserWithStatus(created.id(),r.adminUsername(),app.contactName(),passwordEncoder.encode(bootstrapPassword),"DISABLED"); Long userId=provisioning.findUserId(created.id(),r.adminUsername()); if(userId==null) throw new IllegalStateException("Initial administrator was not created");
        provisioning.insertAdminRole(created.id()); provisioning.insertStandardTenantRoles(created.id()); provisioning.insertNoPermissionRole(created.id()); Long roleId=provisioning.findRoleId(created.id(),"MERCHANT_ADMIN"); List<Long> permissionIds=provisioning.findPermissionIds(INITIAL_ADMIN_PERMISSIONS); if(roleId==null||permissionIds==null||permissionIds.size()!=INITIAL_ADMIN_PERMISSIONS.size()) throw new IllegalStateException("Tenant permission provisioning failed"); provisioning.bindAdmin(created.id(),userId,roleId); provisioning.bindRolePermissions(roleId,permissionIds);
        LocalDateTime now=LocalDateTime.now(clock); if(mapper.approve(id,r.version(),r.reviewRemark(),created.id(),userId,reviewerId,now)!=1) throw error("COMMON-1005",409); String invitationToken=token(); mapper.insertInvitation(id,created.id(),userId,digest(invitationToken),now.plusDays(7)); tenantAudit.insert(created.id(),reviewerId,"CREATE","tenant",created.id(),requestId,"SUCCESS","Created from onboarding application "+app.applicationNo(),now); return new ApprovalResult(mapper.findApplication(id),invitationToken);
    }
    @Transactional
    public OnboardingApplication reject(Long id, RejectOnboardingApplicationRequest r, Long reviewerId) { OnboardingApplication app=required(id); if(!"PENDING".equals(app.status())) throw error("ONBOARDING-1001",409); if(mapper.reject(id,r.version(),r.reviewRemark(),reviewerId,LocalDateTime.now(clock))!=1) throw error("COMMON-1005",409); return required(id); }
    @Transactional
    public ActivationResponse activate(ActivationRequest r) { LocalDateTime now=LocalDateTime.now(clock); OnboardingMapper.Invitation invite=mapper.findInvitationByTokenHash(digest(r.invitationToken())); if(invite==null||invite.usedAt()!=null||!invite.expiresAt().isAfter(now)) throw error("ONBOARDING-1003",422); if(mapper.markInvitationUsed(invite.id(),now)!=1) throw error("ONBOARDING-1004",409); if(mapper.activateUser(invite.tenantId(),invite.userId(),passwordEncoder.encode(r.password()))!=1) throw error("ONBOARDING-1004",409); return new ActivationResponse(invite.tenantCode(),invite.username(),"ACTIVE"); }
    private GuestEstimateResponse estimateResponse(String ref) { return new GuestEstimateResponse(ref,"RECEIVED",false,"这是预估询价线索，非正式报价；平台人员将联系您确认运输需求。",OffsetDateTime.now(clock)); }
    private GuestEstimateLead requiredEstimateLead(Long leadId) { GuestEstimateLead lead = mapper.findEstimateLead(leadId); if (lead == null) throw error("COMMON-1006", 404); return lead; }
    private OnboardingApplication required(Long id){OnboardingApplication app=mapper.findApplication(id);if(app==null)throw error("COMMON-1006",404);return app;}
    private String applicationHash(OnboardingApplication app){return digest(app.companyName()+"\n"+app.contactName()+"\n"+app.businessEmail()+"\n"+app.contactPhone()+"\n"+app.countryCode());}
    private String blank(String v){return v==null||v.isBlank()?null:v;}
    private GuestEstimateLeadStatus parseLeadStatus(String value) { if (value == null || value.isBlank()) return null; try { return GuestEstimateLeadStatus.valueOf(value); } catch (IllegalArgumentException invalid) { throw error("COMMON-1001", 400); } }
    private boolean canTransition(GuestEstimateLeadStatus from, GuestEstimateLeadStatus to) {
        if (from == null || to == null || from == to) return false;
        return switch (from) { case RECEIVED -> to == GuestEstimateLeadStatus.CONTACTING || to == GuestEstimateLeadStatus.CLOSED; case CONTACTING -> to == GuestEstimateLeadStatus.QUALIFIED || to == GuestEstimateLeadStatus.CLOSED; case QUALIFIED -> to == GuestEstimateLeadStatus.CLOSED; case CLOSED -> false; };
    }
    private LocalDateTime utc(OffsetDateTime value) { return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
    private void requireKey(String key){if(key==null||key.isBlank()||key.length()>128)throw error("COMMON-1001",400);}
    private OnboardingException error(String code,int status){return new OnboardingException(code,status);}
    private String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public record ApprovalResult(OnboardingApplication application, String invitationToken) {}
}
