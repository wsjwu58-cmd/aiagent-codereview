package com.heima.codereview.api.controller;

import com.heima.codereview.api.service.AuthService;
import com.heima.codereview.common.model.auth.AuthResponse;
import com.heima.codereview.common.model.auth.LoginRequest;
import com.heima.codereview.common.model.auth.RegisterRequest;
import com.heima.codereview.common.result.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
