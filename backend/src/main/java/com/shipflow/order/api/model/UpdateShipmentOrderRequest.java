package com.shipflow.order.api.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotNull; import java.util.List;
/** Only non-pricing DRAFT fields are accepted; all package, route and quote fields are forbidden. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateShipmentOrderRequest(@NotNull Long version, @NotNull @Valid CreateShipmentOrderRequest.Address senderAddress,
                                         @NotNull @Valid CreateShipmentOrderRequest.Address receiverAddress,
                                         @NotNull List<CreateShipmentOrderRequest.Item> items, String remark,
                                         Object channelId, Object destinationCountry, Object declaredWeight, Object declaredLength,
                                         Object declaredWidth, Object declaredHeight, Object packageCount, Object priceRuleVersion,
                                         Object chargeableWeight, Object amount, Object currency, Object quoteSnapshot) { }
