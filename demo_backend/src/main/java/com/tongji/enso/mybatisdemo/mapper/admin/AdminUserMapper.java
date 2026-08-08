package com.tongji.enso.mybatisdemo.mapper.admin;

import com.tongji.enso.mybatisdemo.entity.admin.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AdminUserMapper {
    @Select("SELECT id, username, password_hash AS passwordHash, enabled FROM admin_users WHERE username = #{username} LIMIT 1")
    AdminUser findByUsername(@Param("username") String username);
}
