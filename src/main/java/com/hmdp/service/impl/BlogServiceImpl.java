package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.jws.soap.SOAPBinding;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstant.BLOG_LIKED;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBolg(Long id) {

        // 判读当前用户是否点赞
        Long userID = UserHolder.getUser().getId();

        String key = BLOG_LIKED + id;
        // 判断集合中是否有元素
        Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString()); // 返回的是一个分数。这个分数由开发者指定，这里指定的是时间戳
        // 可点赞数据库 + 1 且保存到redis 中
        if (score == null) { //
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userID.toString(), System.currentTimeMillis());
            }
        } else { // 不能点赞 数据库 - 1 且 从redis 中移除
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, String.valueOf(userID));
            }
        }
        return Result.ok();
    }

    @Override
    public Result likesBlog(Long id) {
        String key = BLOG_LIKED + id;
        Set<String> top5UserJson = stringRedisTemplate.opsForZSet().range(key, 0, 4); // 只获取值 不拿分值 值是用户id 1011
        if (top5UserJson == null) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5UserJson.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id, " + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(userDTOS);
    }


    @Override
    public Result queryBlogById(Long id) {
        // 查询blog
        Blog blog = getById(id);

        if (blog == null) {
            return Result.fail("blog 不存在");
        }
        // 查询用户信息
        queryBlogUser(blog);
        // 查询blog是否被点赞 // 查询blog的时候是能看到点赞标识的
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {

        UserDTO user = UserHolder.getUser();

        if (user == null) {
            return;
        }

        // 判读当前用户是否点赞
        Long userID = UserHolder.getUser().getId();

        String key = "blog:liked:" + blog.getId();
        // // 判断集合中是否有元素
        Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString());

        blog.setIsLike(score != null);
    }


}
