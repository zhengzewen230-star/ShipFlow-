package com.shipflow.tracking.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shipflow.tracking.api.model.TrackingCallbackRequest;
import com.shipflow.tracking.api.model.TrackingCallbackResult;
import com.shipflow.tracking.api.model.TrackingEventInput;
import com.shipflow.tracking.api.model.TrackingEventResult;
import com.shipflow.tracking.config.TrackingCallbackProperties;
import com.shipflow.tracking.domain.ExistingTrackingEvent;
import com.shipflow.tracking.domain.TrackingCallbackIdentity;
import com.shipflow.tracking.domain.TrackingCallbackOrder;
import com.shipflow.tracking.domain.TrackingStatusMapping;
import com.shipflow.tracking.mapper.TrackingCallbackMapper;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class TrackingCallbackApplicationService {
    private final TrackingCallbackMapper mapper;
    private final TrackingCallbackProperties properties;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;

    public TrackingCallbackApplicationService(TrackingCallbackMapper mapper,
                                              TrackingCallbackProperties properties,
                                              ObjectMapper objectMapper,
                                              Validator validator,
                                              Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = RetainedTrackingCallbackException.class)
    public TrackingCallbackResult receive(String providerCode, String timestamp, String signature,
                                          byte[] rawRequestBody, String requestId) {
        TrackingCallbackIdentity identity = authenticate(providerCode, timestamp, signature, rawRequestBody);
        TrackingCallbackRequest request = parseAndValidate(rawRequestBody);
        List<TrackingEventResult> results = new ArrayList<>();
        int accepted = 0;
        int duplicated = 0;
        for (TrackingEventInput event : request.events()) {
            TrackingEventResult result = process(identity, event, requestId);
            results.add(result);
            if ("DUPLICATE".equals(result.status())) {
                duplicated++;
            } else {
                accepted++;
            }
        }
        return new TrackingCallbackResult(accepted, duplicated, 0, results);
    }

    private TrackingCallbackIdentity authenticate(String providerCode, String timestamp, String signature,
                                                   byte[] rawBody) {
        TrackingCallbackProperties.Credential credential = properties.credential(providerCode);
        if (credential == null || credential.getSystemUserId() == null
                || !validTimestamp(timestamp) || !validSignature(credential, timestamp, signature, rawBody)) {
            throw new TrackingCallbackException("TRACK-1004", 401);
        }
        Long providerId = mapper.authorizedProviderId(providerCode, credential.getSystemUserId());
        if (providerId == null) {
            throw new TrackingCallbackException("COMMON-1004", 403);
        }
        return new TrackingCallbackIdentity(providerId, credential.getSystemUserId());
    }

    private boolean validTimestamp(String timestamp) {
        try {
            if (timestamp == null || !timestamp.matches("^[0-9]{10}$")) {
                return false;
            }
            long requestEpoch = Long.parseLong(timestamp);
            long nowEpoch = Instant.now(clock).getEpochSecond();
            return Math.abs(nowEpoch - requestEpoch) <= properties.getMaxClockSkew().toSeconds();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean validSignature(TrackingCallbackProperties.Credential credential, String timestamp,
                                   String signature, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(credential.decodedKey(), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            byte[] expected = mac.doFinal(rawBody);
            byte[] supplied = Base64.getDecoder().decode(signature == null ? "" : signature);
            return MessageDigest.isEqual(expected, supplied);
        } catch (Exception exception) {
            return false;
        }
    }

    private TrackingCallbackRequest parseAndValidate(byte[] rawBody) {
        final TrackingCallbackRequest request;
        try {
            request = objectMapper.readValue(rawBody, TrackingCallbackRequest.class);
        } catch (Exception exception) {
            throw new TrackingCallbackException("COMMON-1008", 400);
        }
        if (!validator.validate(request).isEmpty()
                || request.events().stream().anyMatch(event -> !event.rawPayload().isObject())) {
            throw new TrackingCallbackException("COMMON-1001", 400);
        }
        return request;
    }

    private TrackingEventResult process(TrackingCallbackIdentity identity, TrackingEventInput event,
                                        String requestId) {
        TrackingCallbackOrder order = mapper.lockOrder(identity.providerId(), event.trackingNo());
        if (order == null) {
            throw new TrackingCallbackException("TRACK-1005", 404);
        }
        String rawPayload = json(event.rawPayload());
        ExistingTrackingEvent existing = mapper.findEvent(identity.providerId(), event.trackingNo(), event.eventId());
        if (existing != null) {
            if (!samePayload(existing.rawPayload(), event.rawPayload())) {
                throw new TrackingCallbackException("TRACK-1003", 409);
            }
            return TrackingEventResult.duplicate(event.eventId());
        }

        String target = TrackingStatusMapping.targetStatus(event.eventCode()).orElse(null);
        TrackingStatusMapping.Decision decision = target == null
                ? TrackingStatusMapping.Decision.ILLEGAL
                : TrackingStatusMapping.decide(order.currentStatus(), target);
        LocalDateTime receivedTime = LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        String processStatus = decision == TrackingStatusMapping.Decision.ILLEGAL ? "RETRY" : "PROCESSED";
        String processMessage = decision == TrackingStatusMapping.Decision.ILLEGAL
                ? (target == null ? "UNSUPPORTED_EVENT_CODE" : "ILLEGAL_STATUS_TRANSITION")
                : null;
        try {
            mapper.insertEvent(order.tenantId(), identity.providerId(), order.orderId(), event.trackingNo(),
                    event.eventId(), event.eventCode(), event.eventDescription(),
                    LocalDateTime.ofInstant(event.eventTime().toInstant(), ZoneOffset.UTC), receivedTime,
                    rawPayload, processStatus, processMessage);
        } catch (DuplicateKeyException exception) {
            ExistingTrackingEvent concurrent = mapper.findEvent(identity.providerId(), event.trackingNo(), event.eventId());
            if (concurrent != null && samePayload(concurrent.rawPayload(), event.rawPayload())) {
                return TrackingEventResult.duplicate(event.eventId());
            }
            throw new TrackingCallbackException("TRACK-1003", 409);
        }
        if (decision == TrackingStatusMapping.Decision.ILLEGAL) {
            throw new RetainedTrackingCallbackException("TRACK-1002", 422);
        }

        boolean advanced = decision == TrackingStatusMapping.Decision.ADVANCE;
        if (advanced && mapper.advanceOrder(order.tenantId(), order.orderId(), order.currentStatus(), target) != 1) {
            throw new TrackingCallbackException("TRACK-1003", 409);
        }
        mapper.insertAudit(order.tenantId(), identity.systemUserId(), order.orderId(), requestId,
                auditDetail(event, order.currentStatus(), target, advanced), receivedTime);
        return TrackingEventResult.accepted(event.eventId());
    }

    private boolean samePayload(String persisted, JsonNode incoming) {
        try {
            return objectMapper.readTree(persisted).equals(incoming);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new TrackingCallbackException("COMMON-1008", 400);
        }
    }

    private String auditDetail(TrackingEventInput event, String fromStatus, String targetStatus,
                               boolean advanced) {
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("eventId", event.eventId());
        detail.put("eventCode", event.eventCode());
        detail.put("fromStatus", fromStatus);
        detail.put("targetStatus", targetStatus);
        detail.put("statusAdvanced", advanced);
        return json(detail);
    }
}
