package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.injector.methods.DeleteById;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {



    @Override
    public Result follow(Long followUsersID, Boolean isFollow) {

        Long userID = UserHolder.getUser().getId();
        if (isFollow) {
            Follow follow = new Follow();
            follow.setUserId(userID);
            follow.setFollowUserId(followUsersID);
            save(follow);
        }
        else {
            remove(new QueryWrapper<Follow>()
                    .eq("user_id",userID)
                    .eq("follow_user_id",followUsersID));
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUsersID) {
        Long userID = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userID)
                .eq("follow_user_id", followUsersID).count();

        return Result.ok(count>0);
    }
}
