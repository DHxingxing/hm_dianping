package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.injector.methods.DeleteById;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstant.FOLLOWS;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUsersID, Boolean isFollow) {

        Long userID = UserHolder.getUser().getId();
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userID);
            follow.setFollowUserId(followUsersID);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(FOLLOWS + userID.toString(), followUsersID.toString());
            }
        } else {
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userID)
                    .eq("follow_user_id", followUsersID));

            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(FOLLOWS + userID.toString(), followUsersID.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUsersID) {
        Long userID = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userID)
                .eq("follow_user_id", followUsersID).count();

        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {

        Long userID = UserHolder.getUser().getId();
        String key1 = FOLLOWS + userID;
        String key2 = FOLLOWS + id;

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);

        if (intersect == null || intersect.isEmpty()) { // intersect == null 和 intersect.isEmpty() 是不一样的
            /*
            1. intersect == null
            情况：当 stringRedisTemplate.opsForSet().intersect(key1, key2) 返回 null 时，说明 Redis 操作可能失败，或者 key1 和 key2 可能不存在。
            作用：防止 NullPointerException，确保 intersect 不是 null，否则 intersect.isEmpty() 会抛出异常。
            2. intersect.isEmpty()
            情况：当 intersect 是一个空集合（Set<String>），说明 key1 和 key2 在 Redis 中确实存在，但它们之间没有交集。
            作用：即使 intersect 不是 null，但没有任何值，我们也不需要继续执行 SQL 查询。
             */
            return Result.ok(Collections.emptyList());
        }

        List<Long> usersID = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(usersID);

        List<UserDTO> usersDTO = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(usersDTO);
    }
}
