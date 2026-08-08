package com.tongji.enso.mybatisdemo.admin.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper {
    @Select("SELECT id, username, password_hash AS passwordHash, enabled " +
            "FROM admin_user WHERE username = #{username} LIMIT 1")
    AdminUser findByUsername(@Param("username") String username);
}
