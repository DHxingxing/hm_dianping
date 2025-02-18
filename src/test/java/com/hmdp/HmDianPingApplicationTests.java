package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RegexUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstant.*;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource

    private ShopServiceImpl shopService;

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 30L);
    }

    @Resource
    private RedisIdWorker redisIdWorker;


    @Test
    void nextID() {
        long l = redisIdWorker.nextID("dhx");
        System.out.println(l);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    UserServiceImpl userService;

    @Test
    public void loginForAllUsers() {
        List<User> users = userService.getMysqlUsers();
        // 遍历所有用户登录请求
        for (User user : users) {
            String phone = user.getPhone();
            if (RegexUtils.isPhoneInvalid(user.getPhone())) {
                System.out.println("手机号格式错误");
            }
            String code = RandomUtil.randomNumbers(6);

            stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

            System.out.println("生成的验证码是" + code);


            // 生成唯一标识符
            String token = UUID.randomUUID().toString(true);

            // 复制属性 user --> UserDTO.class
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            // userDTO 转为 usermap
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);

            // 将原来long类型转为string类型，并放入新的stringUserMap
            Map<String, String> stringUserMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : userMap.entrySet()) {
                if (entry.getValue() instanceof Long) {
                    stringUserMap.put(entry.getKey(), String.valueOf(entry.getValue()));
                } else {
                    stringUserMap.put(entry.getKey(), entry.getValue().toString());
                }
            }

            // 将用户信息存储到 Redis
            stringRedisTemplate.opsForHash().putAll(LOGIN_TOKEN + token, stringUserMap);
            // 设置 token 有效期
            stringRedisTemplate.expire(LOGIN_TOKEN + token, LOGIN_TOKEN_TTL, TimeUnit.MINUTES);

            // 返回成功的 token

        }

        System.out.println("mission all over");
    }


}
