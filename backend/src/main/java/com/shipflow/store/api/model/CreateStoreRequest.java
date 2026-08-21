package com.shipflow.store.api.model;
import jakarta.validation.constraints.*;
public record CreateStoreRequest(@NotBlank @Size(max=64) String storeCode,@NotBlank @Size(max=128) String storeName,@NotBlank @Size(max=64) String platformCode,@NotBlank @Size(max=128) String platformAccount) {}
