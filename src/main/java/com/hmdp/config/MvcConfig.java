package com.hmdp.config;

import com.hmdp.utils.LoginIntercepter;
import com.hmdp.utils.RefreshTokenIntercepter;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginIntercepter())
                .excludePathPatterns("/user/login",
                        "/user/code",
                        "/blog/hot",
                        "/shop/**",
                        "/upload/**").order(1);
        registry.addInterceptor(new RefreshTokenIntercepter(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
