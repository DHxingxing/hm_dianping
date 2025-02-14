package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstant.LOGIN_TOKEN;
import static com.hmdp.utils.RedisConstant.LOGIN_TOKEN_TTL;

public class RefreshTokenIntercepter implements HandlerInterceptor {



    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenIntercepter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //controller之后
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }
        // 获取token 基于redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate
                .opsForHash()
                .entries(LOGIN_TOKEN + token);
        // 判断用户是否存在
        if (userMap.isEmpty()) {

            return true;
        }
        // 将从redis 中查询到的hash 数据转为 userdto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 存在，保存用户到Threadlocal
        UserHolder.saveUser((UserDTO) userDTO);
        // 刷新token有效期
        stringRedisTemplate.expire(LOGIN_TOKEN + token ,LOGIN_TOKEN_TTL, TimeUnit.MINUTES);
        // 放行
        return true;
    }

    //controller之前
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    //渲染之后
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
