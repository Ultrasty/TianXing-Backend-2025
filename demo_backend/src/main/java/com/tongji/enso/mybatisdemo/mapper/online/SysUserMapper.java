package com.tongji.enso.mybatisdemo.mapper.online;

import com.tongji.enso.mybatisdemo.entity.online.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {
    /**
     * 根据用户名查询用户
     */
    SysUser selectUserByUserName(@Param("userName") String userName);

    /**
     * 根据用户ID查询用户
     */
    SysUser selectUserById(@Param("userId") Long userId);
}
