package com.shipflow.common.exception;

import com.shipflow.common.api.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.security.refresh.RefreshTokenAuthenticationException;
import com.shipflow.tenant.application.TenantException;
import com.shipflow.store.application.StoreException;
import com.shipflow.user.application.UserException;
import com.shipflow.rbac.application.RbacException;
import com.shipflow.logistics.application.LogisticsException;
import com.shipflow.quote.application.QuoteException;
import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.warehouse.application.WarehouseException;
import com.shipflow.tracking.application.TrackingCallbackException;
import com.shipflow.exceptioncase.application.ExceptionClaimException;
import com.shipflow.billing.application.BillingException;
import com.shipflow.audit.application.AuditQueryException;
import com.shipflow.onboarding.application.OnboardingException;
import com.shipflow.sf.application.SfIntegrationException;
import com.shipflow.sf.application.SfBusinessException;
import com.shipflow.common.trace.TraceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LoginIdentityAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleLoginFailure(LoginIdentityAuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, new ApiErrorResponse("AUTH-1001", "Authentication failed"));
    }

    @ExceptionHandler(RefreshTokenAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshFailure(RefreshTokenAuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, new ApiErrorResponse("AUTH-1002", "Refresh authentication failed"));
    }

    @ExceptionHandler(TenantException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantFailure(TenantException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), "Tenant operation failed"));
    }

    @ExceptionHandler(StoreException.class)
    public ResponseEntity<ApiErrorResponse> handleStoreFailure(StoreException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), storeMessage(exception.code())));
    }

    private String storeMessage(String code) {
        return switch (code) {
            case "COMMON-1001" -> "请求参数不完整或格式不正确";
            case "COMMON-1005" -> "店铺已被其他操作更新，请刷新后重试";
            case "COMMON-1006" -> "店铺不存在或当前租户无权访问";
            case "COMMON-1009" -> "幂等键不能用于不同的店铺请求";
            case "COMMON-1010" -> "该店铺请求正在处理中，请稍后重试";
            case "STORE-1001" -> "店铺编码或平台账号已存在";
            case "STORE-1002" -> "店铺当前状态不允许执行该操作";
            case "STORE-1004" -> "物流渠道不可用或不服务当前店铺国家";
            default -> "店铺操作失败";
        };
    }

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ApiErrorResponse> handleUserFailure(UserException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), "User operation failed"));
    }

    @ExceptionHandler(RbacException.class)
    public ResponseEntity<ApiErrorResponse> handleRbacFailure(RbacException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), "RBAC operation failed"));
    }

    @ExceptionHandler(LogisticsException.class)
    public ResponseEntity<ApiErrorResponse> handleLogisticsFailure(LogisticsException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), "Logistics master-data operation failed"));
    }

    @ExceptionHandler(QuoteException.class)
    public ResponseEntity<ApiErrorResponse> handleQuoteFailure(QuoteException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), "Quote query failed"));
    }

    @ExceptionHandler(ShipmentOrderException.class)
    public ResponseEntity<ApiErrorResponse> handleShipmentOrderFailure(ShipmentOrderException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), shipmentOrderMessage(exception.code())));
    }

    private String shipmentOrderMessage(String code) {
        return switch (code) {
            case "COMMON-1001" -> "请求参数不完整或格式不正确";
            case "COMMON-1004" -> "当前账号无权创建订单";
            case "COMMON-1005" -> "订单已被其他操作更新，请刷新后重试";
            case "COMMON-1006" -> "订单不存在或当前租户无权访问";
            case "COMMON-1009" -> "幂等键不能用于不同的下单请求";
            case "COMMON-1010" -> "该下单请求正在处理中，请勿重复提交";
            case "QUOTE-1003" -> "报价已过期，无法创建订单";
            case "QUOTE-1004" -> "该报价已经创建过订单";
            case "ORDER-1003" -> "报价条件或报价快照不完整，无法创建订单";
            case "ORDER-1010" -> "订单当前不在费用确认流程中，或费用调整已处理";
            case "ORDER-1011" -> "提交的费用与服务端费用调整不一致";
            default -> "订单操作失败";
        };
    }
    @ExceptionHandler(WarehouseException.class)
    public ResponseEntity<ApiErrorResponse> handleWarehouseFailure(WarehouseException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), warehouseMessage(exception.code())));
    }

    private String warehouseMessage(String code) {
        return switch (code) {
            case "WAREHOUSE-1001" -> "订单当前不在待入库状态，无法执行入库";
            case "WAREHOUSE-1002" -> "订单当前不允许复称，或复称数据不合法";
            case "WAREHOUSE-1003" -> "订单缺少可用的物流渠道，暂时无法出库";
            case "WAREHOUSE-1004" -> "订单当前不在待出库状态，无法执行出库";
            case "WAREHOUSE-1005" -> "该订单已经有出库记录，请勿重复操作";
            case "COMMON-1001" -> "请求参数不完整或格式不正确";
            case "COMMON-1004" -> "当前账号无权执行仓库操作";
            case "COMMON-1005" -> "订单已被其他操作更新，请刷新后重试";
            case "COMMON-1006" -> "订单不存在或当前租户无权访问";
            case "COMMON-1009" -> "幂等键不能用于不同的仓库操作";
            case "COMMON-1010" -> "该仓库操作正在处理中，请稍后重试";
            default -> "仓库操作失败";
        };
    }

    @ExceptionHandler(TrackingCallbackException.class)
    public ResponseEntity<ApiErrorResponse> handleTrackingCallbackFailure(TrackingCallbackException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), "Tracking callback failed"));
    }

    @ExceptionHandler(ExceptionClaimException.class)
    public ResponseEntity<ApiErrorResponse> handleExceptionClaimFailure(ExceptionClaimException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), "Exception or claim operation failed"));
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<ApiErrorResponse> handleBillingFailure(BillingException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), billingMessage(exception.code())));
    }
    private String billingMessage(String code) {
        return switch (code) {
            case "COMMON-1001" -> "请求参数不完整或格式不正确";
            case "COMMON-1006" -> "账单资源不存在或当前租户无权访问";
            case "COMMON-1009" -> "幂等键不能用于不同的物流商或账单文件内容";
            case "COMMON-1010" -> "该账单导入请求正在处理中，请稍后重试";
            case "BILL-1001" -> "账单文件或导入参数不合法";
            case "BILL-1002" -> "账单导入批次发生并发冲突，请稍后重试";
            case "BILL-1003" -> "账单明细重复";
            default -> "账单操作失败";
        };
    }
    @ExceptionHandler(AuditQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleAuditFailure(AuditQueryException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), "Audit query failed"));
    }

    @ExceptionHandler(OnboardingException.class)
    public ResponseEntity<ApiErrorResponse> handleOnboardingFailure(OnboardingException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), "Onboarding operation failed"));
    }

    @ExceptionHandler(SfBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleSfBusinessFailure(SfBusinessException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(SfIntegrationException.class)
    public ResponseEntity<ApiErrorResponse> handleSfFailure(SfIntegrationException exception) {
        return response(HttpStatus.valueOf(exception.status()), new ApiErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST,
                new ApiErrorResponse("COMMON-1001", "请求参数错误", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return response(HttpStatus.BAD_REQUEST,
                new ApiErrorResponse("COMMON-1001", "请求参数错误"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST,
                new ApiErrorResponse("COMMON-1008", "请求体格式错误"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (exception.getSupportedHttpMethods() != null) {
            builder.allow(exception.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]));
        }
        return builder.body(new ApiErrorResponse("COMMON-1001", "Method not allowed"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException exception) {
        return response(HttpStatus.BAD_REQUEST,
                new ApiErrorResponse("COMMON-1001", "Missing required request header",
                        Map.of("header", exception.getHeaderName())));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingResource(NoResourceFoundException exception) {
        return response(HttpStatus.NOT_FOUND,
                new ApiErrorResponse("COMMON-1006", "Resource not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        String traceId = TraceId.currentOrCreate();
        log.error("Unexpected error, traceId={}, exceptionType={}, message={}",
                traceId, exception.getClass().getName(), exception.getMessage(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                new ApiErrorResponse("COMMON-1007", "内部系统错误"));
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, ApiErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }
}
