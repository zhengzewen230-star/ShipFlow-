package com.shipflow.store.api.model;
import jakarta.validation.constraints.*;
public record StoreStatusChangeRequest(@NotBlank String status,@NotNull Long version) {}
