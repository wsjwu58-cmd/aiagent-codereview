package com.heima.codereview.api.service;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    public String profile(String token) {
        return "当前用户令牌长度: " + (token == null ? 0 : token.length());
    }
}
