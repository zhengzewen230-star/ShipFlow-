package com.shipflow.store.api.model;

import jakarta.validation.constraints.NotNull;

public record SetDefaultStoreChannelRequest(@NotNull Long channelId, @NotNull Long version) {
}
