package com.shipflow.exceptioncase;
import com.shipflow.exceptioncase.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ExceptionClaimStateMachineTest {
 @Test void exceptionWorkflowIsStrictlyForward(){assertThat(ExceptionStateMachine.canAssign("OPEN")).isTrue();assertThat(ExceptionStateMachine.canTransition("OPEN","PROCESSING")).isTrue();assertThat(ExceptionStateMachine.canTransition("PROCESSING","WAITING_PROVIDER_FEEDBACK")).isTrue();assertThat(ExceptionStateMachine.canTransition("WAITING_PROVIDER_FEEDBACK","PROCESSING")).isTrue();assertThat(ExceptionStateMachine.canTransition("PROCESSING","PENDING_FINANCE_CONFIRMATION")).isTrue();assertThat(ExceptionStateMachine.canTransition("PENDING_FINANCE_CONFIRMATION","RESOLVED")).isTrue();assertThat(ExceptionStateMachine.canTransition("RESOLVED","CLOSED")).isTrue();assertThat(ExceptionStateMachine.canTransition("PENDING_FINANCE_CONFIRMATION","CLOSED")).isFalse();assertThat(ExceptionStateMachine.canTransition("OPEN","RESOLVED")).isFalse();assertThat(ExceptionStateMachine.canTransition("CLOSED","RESOLVED")).isFalse();}
 @Test void claimWorkflowRequiresSubmissionBeforeDecision(){assertThat(ClaimStateMachine.canSubmit("OPEN")).isTrue();assertThat(ClaimStateMachine.canResolve("SUBMITTED","APPROVED")).isTrue();assertThat(ClaimStateMachine.canResolve("SUBMITTED","REJECTED")).isTrue();assertThat(ClaimStateMachine.canResolve("OPEN","APPROVED")).isFalse();assertThat(ClaimStateMachine.canClose("APPROVED")).isTrue();assertThat(ClaimStateMachine.canClose("SUBMITTED")).isFalse();}
}
