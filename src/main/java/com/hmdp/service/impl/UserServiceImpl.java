package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstant.*;
import static com.hmdp.utils.SystemConstants.CACHE_CODE;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*
     * 实现发送短信验证码的功能
     *   todo：接入第三方云平台
     *  */
    @Override
    public Result senCode(String phone, HttpSession session) {
//        校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);

//        session.setAttribute("code", code);
//        String code1 = (String) session.getAttribute("code");
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        System.out.println("生成的验证码是" + code);

        log.debug("发送成功，验证码: {}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // 1、校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        // 2、redis校验验证码
        String code = loginForm.getCode();
//      CACHE_CODE = (String) session.getAttribute("code");
        CACHE_CODE = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        // 3、不一致报错

        if (CACHE_CODE == null) {
            return Result.fail("验证码已经过期");
        }
        if (!CACHE_CODE.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 4、一致根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        /*
        UUID（Universally Unique Identifier）是一个标准的标识符，
        用于唯一地标识信息、对象、资源或数据。UUID 是一个 128 位的数字，
        通常用 16 进制的形式表示，确保在全球范围内的唯一性。
        UUID 在很多分布式系统中被广泛使用，用来生成唯一的标识符，
        例如用户 ID、订单号、事务 ID 等。
         */

        if (user == null) {
            // 不存在则 创建用户
            user = createUserWithPhone(phone);
        }
        // 唯一标识符
        String token = UUID.randomUUID().toString(true);
        // 复制属性 user --> UserDTO.class
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // userDTO 转为 usermap
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        // 通过新建一个hashmap 将 原来的long类型转为string类型 ，但是提升了时间复杂度
        Map<String, String> stringUserMap = new HashMap<>();
        /*todo: 如何优化时间复杂度*/
        // 将usermap中的非string类型转位string类型 因为StringRedisTemplate 是 一个Map<String, String>
        for (Map.Entry<String, Object> entry : userMap.entrySet()) {
            if (entry.getValue() instanceof Long) {
                stringUserMap.put(entry.getKey(), String.valueOf(entry.getValue()));
            } else {
                stringUserMap.put(entry.getKey(), entry.getValue().toString());
            }
        }

        stringRedisTemplate.opsForHash().putAll(LOGIN_TOKEN + token, stringUserMap);

        //设置token有效期
        stringRedisTemplate.expire(LOGIN_TOKEN + token, LOGIN_TOKEN_TTL, TimeUnit.MINUTES);
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok(token);

    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        save(user);
        return user;

    }

    /**
     * 获取全部用户数据
     * @return 用户列表
     */
    public List<User> getMysqlUsers() {
        return this.list(); // 调用 MyBatis-Plus 的 list() 方法
    }


}
