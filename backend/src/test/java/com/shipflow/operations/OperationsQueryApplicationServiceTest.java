package com.shipflow.operations;

import com.shipflow.operations.application.OperationsQueryApplicationService;
import com.shipflow.operations.domain.OperationsSummary;
import com.shipflow.operations.domain.OperationsTodos;
import com.shipflow.operations.mapper.OperationsMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationsQueryApplicationServiceTest {
    private final OperationsMapper mapper = mock(OperationsMapper.class);
    private final OperationsQueryApplicationService service = new OperationsQueryApplicationService(mapper);
    @Test void queriesOnlyTheJwtTenant() {
        when(mapper.findSummary(7L)).thenReturn(new OperationsSummary(1,2,3,4,5,6,7,8,9,10));
        when(mapper.findTodos(7L)).thenReturn(new OperationsTodos(4,3,2,1));
        assertThat(service.summary(7L).pendingInbound()).isEqualTo(2);
        assertThat(service.todos(7L).submittedClaims()).isEqualTo(1);
        verify(mapper).findSummary(7L); verify(mapper).findTodos(7L); verifyNoMoreInteractions(mapper);
    }
}
