package com.shipflow.order.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.order.api.model.CreateShipmentOrderRequest;
import com.shipflow.order.api.model.ShipmentOrderResponse;
import com.shipflow.order.application.ShipmentOrderApplicationService;
import com.shipflow.quote.application.QuoteException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/quotes/{quoteId}/shipment-orders")
public class ShipmentOrderController {
 private final ShipmentOrderApplicationService service; public ShipmentOrderController(ShipmentOrderApplicationService service){this.service=service;}
 @PostMapping public ResponseEntity<ApiResponse<ShipmentOrderResponse>> create(@PathVariable Long quoteId,@Valid @RequestBody CreateShipmentOrderRequest request,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt){return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(tenant(jwt),Long.valueOf(jwt.getSubject()),quoteId,request,key,requestId)));}
 private Long tenant(Jwt jwt){try{return jwt==null?null:Long.valueOf(jwt.getClaimAsString("tenant_id"));}catch(Exception e){throw new QuoteException("COMMON-1004",403);}}
}
