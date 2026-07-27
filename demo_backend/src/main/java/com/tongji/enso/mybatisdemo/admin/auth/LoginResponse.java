package com.tongji.enso.mybatisdemo.admin.auth;

public class LoginResponse {
    private final String token;
    private final String tokenType;
    private final long expiresIn;

    public LoginResponse(String token, long expiresIn) {
        this.token = token;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }
}
