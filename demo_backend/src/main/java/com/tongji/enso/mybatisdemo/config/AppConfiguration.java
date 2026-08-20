package com.tongji.enso.mybatisdemo.config;

import com.tongji.enso.mybatisdemo.config.admin.AdminAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class AppConfiguration {
    @Autowired
    private AdminAuthInterceptor adminAuthInterceptor;

    @Value("${admin.upload.root:uploads}")
    private String uploadRoot;

    @Value("${admin.upload.public-prefix:/admin-files}")
    private String publicPrefix;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Authorization");
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(adminAuthInterceptor)
                        .addPathPatterns("/admin/**")
                        .excludePathPatterns("/admin/auth/login", "/admin/auth/logout");
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
                registry.addResourceHandler(normalizePublicPrefix(publicPrefix) + "/**")
                        .addResourceLocations(root.toUri().toString());
            }
        };
    }

    private String normalizePublicPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return "/admin-files";
        }
        String normalized = prefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}