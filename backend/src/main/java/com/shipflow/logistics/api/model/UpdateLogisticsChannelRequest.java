package com.shipflow.logistics.api.model;
import com.shipflow.logistics.domain.model.LogisticsChannel.TransportMode; import jakarta.validation.constraints.*;
public record UpdateLogisticsChannelRequest(@NotBlank @Size(max=128) String channelName, @NotNull TransportMode transportMode, @NotBlank @Size(max=255) String serviceArea, @NotBlank String status, @NotNull Long version) { }
