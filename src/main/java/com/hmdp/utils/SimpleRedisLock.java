package com.hmdp.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "LOCK:";
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    } // 类一加载，这里的 静态资源就初始化了


    @Override
    public boolean tryLock(Long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + this.name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT // 保证判断和删除的原则性
                , Collections.singletonList(KEY_PREFIX + this.name) // 执行单元素的集合,将元素放到list中去 因为要求传入的是list集合
                , ID_PREFIX + Thread.currentThread().getId()); // 以前获取锁的标识和删除锁的操作是两行代码，在代码之间会出现并发安全问题，现在是一个脚本了

    }

//    @Override
//    public void unlock() {
//
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (id.equals(threadId)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//
//
//    }
}
