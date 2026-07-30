package com.tongji.enso.mybatisdemo.entity.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminUser {
    private Integer id;
    private String username;
    private String passwordHash;
    private Integer enabled;

    public boolean isEnabled() {
        return enabled != null && enabled == 1;
    }
}
