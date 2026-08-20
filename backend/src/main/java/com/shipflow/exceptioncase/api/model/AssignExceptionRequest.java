package com.shipflow.exceptioncase.api.model;
import jakarta.validation.constraints.*;
public record AssignExceptionRequest(@NotNull @Positive Long assignedToUserId,
                                     @NotBlank @Pattern(regexp="MERCHANT|PROVIDER|CUSTOMS|CUSTOMER|OTHER") String responsibleParty,
                                     @Size(max=1000) String reason,
                                     @NotNull @PositiveOrZero Long version) {
    public AssignExceptionRequest(Long assignedToUserId, String reason, Long version) {
        this(assignedToUserId, "OTHER", reason, version);
    }
}
