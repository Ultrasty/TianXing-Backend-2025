package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public enum ImportMode {
    REJECT,
    UPSERT;

    public static ImportMode parse(String value) {
        if (value != null) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Converted to the stable API error below.
            }
        }
        throw new AdminException("IMPORT_FILE_INVALID", "导入模式必须是 REJECT 或 UPSERT", HttpStatus.BAD_REQUEST);
    }
}
