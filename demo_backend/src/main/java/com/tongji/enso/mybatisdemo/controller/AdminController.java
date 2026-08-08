package com.tongji.enso.mybatisdemo.controller;

import com.tongji.enso.mybatisdemo.config.JwtUtils;
import com.tongji.enso.mybatisdemo.config.Result;
import com.tongji.enso.mybatisdemo.config.SecurityUtils;
import com.tongji.enso.mybatisdemo.entity.online.SysUser;
import com.tongji.enso.mybatisdemo.mapper.online.SysUserMapper;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Data
    public static class LoginBody {
        private String userName;
        private String password;
    }

    /**
     * 管理员登录 (对标 RuoYi-Vue 逻辑)
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginBody loginBody) {
        if (loginBody == null || loginBody.getUserName() == null || loginBody.getPassword() == null) {
            return Result.error("用户名和密码不能为空");
        }

        SysUser user = sysUserMapper.selectUserByUserName(loginBody.getUserName());
        if (user == null) {
            return Result.error("用户名或密码错误");
        }

        if ("1".equals(user.getStatus())) {
            return Result.error("该账号已被停用，请联系超级管理员");
        }

        // 调用 SecurityUtils 进行 BCrypt 安全校验 (对标 RuoYi-Vue)
        if (!SecurityUtils.matchesPassword(loginBody.getPassword(), user.getPassword())) {
            return Result.error("用户名或密码错误");
        }

        // 生成 JWT Token
        String token = jwtUtils.createToken(user.getUserName());

        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("userName", user.getUserName());
        map.put("nickName", user.getNickName());

        return Result.success("登录成功", map);
    }

    /**
     * 获取当前登录管理员信息
     */
    @GetMapping("/getInfo")
    public Result<SysUser> getInfo() {
        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (username == null) {
            return Result.error(401, "尚未登录或登录已超时");
        }
        SysUser user = sysUserMapper.selectUserByUserName(username);
        if (user == null) {
            return Result.error("用户不存在");
        }
        // 隐藏密码密文
        user.setPassword(null);
        return Result.success(user);
    }
}
