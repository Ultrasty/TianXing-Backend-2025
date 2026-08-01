package com.tongji.enso.mybatisdemo.admin.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.Console;
import java.util.Arrays;

public final class AdminPasswordHashTool {
    private AdminPasswordHashTool() {
    }

    public static void main(String[] args) {
        String environmentPassword = System.getenv("ADMIN_PASSWORD");
        Console console = System.console();
        if (console == null && (environmentPassword == null || environmentPassword.isEmpty())) {
            throw new IllegalStateException("A real terminal or ADMIN_PASSWORD environment variable is required");
        }
        char[] first = environmentPassword == null ? console.readPassword("Admin password: ")
                : environmentPassword.toCharArray();
        char[] second = environmentPassword == null ? console.readPassword("Confirm password: ")
                : environmentPassword.toCharArray();
        try {
            if (first == null || first.length < 12) {
                throw new IllegalArgumentException("Password must contain at least 12 characters");
            }
            if (!Arrays.equals(first, second)) {
                throw new IllegalArgumentException("Passwords do not match");
            }
            System.out.println(new BCryptPasswordEncoder().encode(new String(first)));
        } finally {
            if (first != null) {
                Arrays.fill(first, '\0');
            }
            if (second != null) {
                Arrays.fill(second, '\0');
            }
        }
    }
}
