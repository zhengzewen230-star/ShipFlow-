package com.shipflow;

import com.shipflow.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/_test")
class TestWebController {

    @GetMapping("/ok")
    ApiResponse<String> ok() {
        return ApiResponse.success("ok");
    }

    @GetMapping("/error")
    ApiResponse<String> error() {
        throw new IllegalStateException("test failure");
    }

    @PostMapping("/validation")
    ApiResponse<String> validation(@Valid @RequestBody TestRequest request) {
        return ApiResponse.success(request.name());
    }

    record TestRequest(@NotBlank String name) {
    }
}
