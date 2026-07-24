package com.tongji.enso.mybatisdemo.entity.online;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
public class SysUser implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String userName;
    private String nickName;
    private String password;
    private String status;
    private Date createTime;
}
