package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMPS = 1739690334L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    // 用于生成订单id
    public long nextID(String prefixKey){
        // 生成时间戳

        LocalDateTime now = LocalDateTime.now();

        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMPS;
        // 生成序列号
        // 获取当前日期的天
        String data = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr" + prefixKey + ":" + data);
        //increment 方法是用于对 Redis 中的字符串值进行递增操作。
        // 如果该键（"icr" + prefixKey + ":"）在 Redis 中不存在，它会将该键的值初始化为 0，然后执行递增操作。

        return timestamp << 32 | count;
    }


}
