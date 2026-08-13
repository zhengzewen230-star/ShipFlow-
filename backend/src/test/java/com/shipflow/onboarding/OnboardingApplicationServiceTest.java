package com.shipflow.onboarding;

import com.shipflow.onboarding.api.model.*;
import com.shipflow.onboarding.application.*;
import com.shipflow.onboarding.domain.model.OnboardingApplication;
import com.shipflow.onboarding.mapper.OnboardingMapper;
import com.shipflow.tenant.domain.model.Tenant;
import com.shipflow.tenant.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnboardingApplicationServiceTest {
    OnboardingMapper mapper; TenantMapper tenants; TenantProvisioningMapper provisioning; TenantAuditMapper audit; OnboardingApplicationService service;
    @BeforeEach void setUp(){mapper=mock(OnboardingMapper.class);tenants=mock(TenantMapper.class);provisioning=mock(TenantProvisioningMapper.class);audit=mock(TenantAuditMapper.class);service=new OnboardingApplicationService(mapper,tenants,provisioning,audit,new BCryptPasswordEncoder(10),Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"),ZoneOffset.UTC));}
    @Test void estimateReplayReturnsSameNonFormalReference(){when(mapper.findEstimateByIdempotencyKey("key")).thenReturn(new OnboardingMapper.IdempotentEstimate("EST-1",shaEstimate())); GuestEstimateResponse response=service.submitEstimate(estimate(),"key"); assertThat(response.referenceNo()).isEqualTo("EST-1");assertThat(response.formalQuote()).isFalse();verify(mapper,never()).insertEstimate(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());}
    @Test void estimateRejectsIdenticalCountries(){assertThatThrownBy(()->service.submitEstimate(new CreateGuestEstimateRequest("CN","CN","AIR",GuestCargoType.GENERAL,"Bluetooth headset",new java.math.BigDecimal("1.000"),new java.math.BigDecimal("0.010000"),"Li","li@example.com","+8613800000000"),"key")).isInstanceOf(OnboardingException.class).extracting("code").isEqualTo("ONBOARDING-1005");}
    @Test void approvalCreatesDisabledAdminAndOneTimeInvitation(){OnboardingApplication app=app(3L,"PENDING",0);when(mapper.findApplication(3L)).thenReturn(app,app(3L,"APPROVED",1));when(tenants.findByCode("MERCHANT_A")).thenReturn(null,new Tenant(8L,"MERCHANT_A","Acme","ACTIVE",0,null,null));when(provisioning.findUserId(8L,"acme_admin")).thenReturn(9L);when(provisioning.findRoleId(8L,"MERCHANT_ADMIN")).thenReturn(10L);when(provisioning.findPermissionIds(anyList())).thenReturn(java.util.List.of(1L,2L,3L,4L,5L,6L,7L,8L));when(mapper.approve(eq(3L),eq(0L),any(),eq(8L),eq(9L),eq(1L),any())).thenReturn(1);
        var result=service.approve(3L,new ApproveOnboardingApplicationRequest("MERCHANT_A","acme_admin","Approved",0L),1L,"r1"); assertThat(result.invitationToken()).hasSizeGreaterThan(32);verify(provisioning).insertAdminUserWithStatus(eq(8L),eq("acme_admin"),eq("Contact"),argThat(hash->hash.startsWith("$2")),eq("DISABLED"));verify(mapper).insertInvitation(eq(3L),eq(8L),eq(9L),any(),any());}
    @Test void activationConsumesInvitationBeforeEnablingUser(){String token="a".repeat(32);when(mapper.findInvitationByTokenHash(any())).thenReturn(new OnboardingMapper.Invitation(1L,8L,9L,"MERCHANT_A","admin",LocalDateTime.parse("2026-08-20T00:00:00"),null));when(mapper.markInvitationUsed(eq(1L),any())).thenReturn(1);when(mapper.activateUser(eq(8L),eq(9L),argThat(hash->hash.startsWith("$2")))).thenReturn(1);assertThat(service.activate(new ActivationRequest(token,"Valid-Password-2026!"))).extracting(ActivationResponse::status).isEqualTo("ACTIVE");}
    private CreateGuestEstimateRequest estimate(){return new CreateGuestEstimateRequest("CN","US","AIR",GuestCargoType.GENERAL,"Bluetooth headset",new java.math.BigDecimal("1.000"),new java.math.BigDecimal("0.010000"),"Li","li@example.com","+8613800000000");}
    private String shaEstimate(){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("CN\nUS\nAIR\nGENERAL\nBluetooth headset\n1.000\n0.010000\nLi\nli@example.com\n+8613800000000".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}}
    private OnboardingApplication app(Long id,String status,long version){return new OnboardingApplication(id,"APP-1","Acme","Contact","a@example.com","123","CN",status,null,null,null,version,null,null);}
}
