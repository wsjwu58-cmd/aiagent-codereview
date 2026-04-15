package com.heima.codereview.api.service;

import com.heima.codereview.common.exception.BizException;
import com.heima.codereview.common.model.auth.AuthResponse;
import com.heima.codereview.common.model.auth.LoginRequest;
import com.heima.codereview.common.model.auth.RegisterRequest;
import com.heima.codereview.common.model.auth.UserInfo;
import com.heima.codereview.common.utils.HashUtils;
import com.heima.codereview.common.utils.IdUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final Map<String, UserAccount> accounts = new ConcurrentHashMap<>();

    public AuthResponse register(RegisterRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new BizException("用户名和密码不能为空");
        }
        if (accounts.containsKey(request.username())) {
            throw new BizException("用户名已存在");
        }

        String userId = IdUtils.withPrefix("user");
        UserAccount account = new UserAccount(userId, request.username(), HashUtils.sha256(request.password()), request.email());
        accounts.put(request.username(), account);

        String token = issueToken(account.userId(), account.username());
        return new AuthResponse(token, new UserInfo(account.userId(), account.username(), account.email()));
    }

    public AuthResponse login(LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new BizException("用户名和密码不能为空");
        }

        UserAccount account = accounts.get(request.username());
        if (account == null || !account.passwordHash().equals(HashUtils.sha256(request.password()))) {
            throw new BizException("用户名或密码错误");
        }

        String token = issueToken(account.userId(), account.username());
        return new AuthResponse(token, new UserInfo(account.userId(), account.username(), account.email()));
    }

    private String issueToken(String userId, String username) {
        String raw = userId + ":" + username + ":" + System.currentTimeMillis();
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private record UserAccount(String userId, String username, String passwordHash, String email) {
    }
}
