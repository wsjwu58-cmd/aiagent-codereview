package com.heima.codereview.common.model.auth;

public record AuthResponse(String token, UserInfo user) {
}
