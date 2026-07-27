package com.tongji.enso.mybatisdemo.admin.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.Console;
import java.util.Arrays;

public final class AdminPasswordHashTool {
    private AdminPasswordHashTool() {
    }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("A real terminal is required so the password is not echoed");
        }
        char[] first = console.readPassword("Admin password: ");
        char[] second = console.readPassword("Confirm password: ");
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
