package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

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
        session.setAttribute("code", code);
        String code1 = (String) session.getAttribute("code");
        System.out.println("生成的验证码是" + code1);
        log.debug("发送成功，验证码: {}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // 1、校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        // 2、校验验证码
        String code = loginForm.getCode();
        CACHE_CODE = (String) session.getAttribute("code");
        // 3、不一致报错

        if(CACHE_CODE == null){
            return Result.fail("验证码已经过期");
        }
        if(!CACHE_CODE.equals(code)){
            return Result.fail("验证码错误");
        }
        // 4、一致根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        if(user == null){
            // 不存在则 创建用户
            user = createUserWithPhone(phone);
        }
        session.setAttribute("user",user);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        save(user); // 并没有保存到数据库中？ 为什么？
        return user;

    }
}
