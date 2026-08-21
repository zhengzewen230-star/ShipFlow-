package com.shipflow.logistics.api.model;
import jakarta.validation.constraints.*; import java.util.List;
public record ServiceCountriesRequest(@NotEmpty List<@Pattern(regexp="[A-Z]{2}") String> countryCodes, @NotNull Long version) { }
