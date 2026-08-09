package com.shipflow.user;

import com.shipflow.user.api.model.*;
import com.shipflow.user.application.UserApplicationService;
import com.shipflow.user.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserApplicationServiceTest {
    @Test void missingIdempotencyKeyIsRejectedBeforePersistence(){
        UserMapper m=mock(UserMapper.class); UserApplicationService s=new UserApplicationService(m,mock(UserIdempotencyMapper.class),mock(UserAuditMapper.class),new BCryptPasswordEncoder(),Clock.systemUTC());
        assertThatThrownBy(()->s.create(1L,new CreateUserRequest("u","U","Tmp!Password123",List.of(2L)),null,9L,"r")).hasMessage("COMMON-1001"); verifyNoInteractions(m);
    }
    @Test void differentIdempotencyBodyConflicts(){
        UserMapper m=mock(UserMapper.class); UserIdempotencyMapper i=mock(UserIdempotencyMapper.class); when(i.find(anyLong(),anyString(),anyString())).thenReturn(new UserIdempotencyMapper.Record("other",1L,"SUCCEEDED"));
        UserApplicationService s=new UserApplicationService(m,i,mock(UserAuditMapper.class),new BCryptPasswordEncoder(),Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThatThrownBy(()->s.create(1L,new CreateUserRequest("u","U","Tmp!Password123",List.of(2L)),"k",9L,"r")).hasMessage("COMMON-1009");
    }
}
