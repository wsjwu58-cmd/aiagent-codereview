package com.heima.codereview.api.controller;

import com.heima.codereview.api.service.UserService;
import com.heima.codereview.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, String>> profile(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.success(Map.of("profile", userService.profile(token)));
    }
}
