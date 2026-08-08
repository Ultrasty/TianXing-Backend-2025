package com.tongji.enso.mybatisdemo.entity.admin;

public class AdminApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public AdminApiResponse() {
    }

    private AdminApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> AdminApiResponse<T> ok(String message, T data) {
        return new AdminApiResponse<T>(true, message, data);
    }

    public static <T> AdminApiResponse<T> fail(String message) {
        return new AdminApiResponse<T>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
