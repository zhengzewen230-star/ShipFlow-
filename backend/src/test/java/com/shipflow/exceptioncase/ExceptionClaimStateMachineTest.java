package com.shipflow.exceptioncase;
import com.shipflow.exceptioncase.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class ExceptionClaimStateMachineTest {
 @Test void exceptionWorkflowIsStrictlyForward(){assertThat(ExceptionStateMachine.canAssign("OPEN")).isTrue();assertThat(ExceptionStateMachine.canTransition("PROCESSING","RESOLVED")).isTrue();assertThat(ExceptionStateMachine.canTransition("RESOLVED","CLOSED")).isTrue();assertThat(ExceptionStateMachine.canTransition("OPEN","RESOLVED")).isFalse();assertThat(ExceptionStateMachine.canTransition("CLOSED","RESOLVED")).isFalse();}
 @Test void claimWorkflowRequiresSubmissionBeforeDecision(){assertThat(ClaimStateMachine.canSubmit("OPEN")).isTrue();assertThat(ClaimStateMachine.canResolve("SUBMITTED","APPROVED")).isTrue();assertThat(ClaimStateMachine.canResolve("SUBMITTED","REJECTED")).isTrue();assertThat(ClaimStateMachine.canResolve("OPEN","APPROVED")).isFalse();assertThat(ClaimStateMachine.canClose("APPROVED")).isTrue();assertThat(ClaimStateMachine.canClose("SUBMITTED")).isFalse();}
}
