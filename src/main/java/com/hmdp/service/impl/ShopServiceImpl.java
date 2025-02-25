package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstant;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
    @Resource
    private IShopService shopService;

    @Override
    public Result queryByID(Long id) {
        // 封装为一个 缓存穿透
        Shop shop = cacheClient.QueryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);


//        String Key = CACHE_SHOP_KEY + id;
//
//        String shopJson = stringRedisTemplate.opsForValue().get(Key);
//        if (StrUtil.isNotBlank(shopJson)) {
//            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
//        }
//        if (shopJson != null) {
//            return Result.fail("店铺不存在");
//        }


//        Shop shop = getById(id);
//        if (shop == null) {
//            stringRedisTemplate.opsForValue().set(Key, "", CACHE_NULL_TTL, TimeUnit.MINUTES); // 为空
//            return Result.fail("店铺不存在");
//        }
//        stringRedisTemplate.opsForValue().set(Key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

    }

    @Override
    @Transactional //@Transactional 是 Spring 框架中用于声明事务管理的注解，抛出异常则全部回滚
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("shop id is null error");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }

    // 逻辑过期：将店铺数据封装到 Redis 缓存中，并设置逻辑过期时间
    public void saveShop2Redis(Long id, Long expireTime) throws InterruptedException {
        // 1. 从数据库获取店铺信息
        Shop shop = getById(id);
        // 模拟延迟，测试用
        Thread.sleep(200);

        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop); // 设置店铺数据
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime)); // 设置过期时间

        // 3. 将数据存入 Redis
        stringRedisTemplate.opsForValue().set(RedisConstant.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData)); //这里的 写入redis的数据 里面的shop类也是json 数据 作为 外部Json 的值
    }

    // 创建固定大小的线程池，用于缓存重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 根据逻辑过期时间解决缓存击穿问题
    public Result QueryWithLogicExpire(Long id) {
        // 1. 构建 Redis 缓存键
        String Key = CACHE_SHOP_KEY + id;

        // 2. 从 Redis 中获取店铺数据
        String shopJson = stringRedisTemplate.opsForValue().get(Key);
        if (StrUtil.isBlank(shopJson)) {
            // 如果缓存为空，返回 null
            return null;
        }

        // 3. 将 JSON 数据反序列化为 RedisData 对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // 将 RedisData 中的 data 字段反序列化为 Shop 对象
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);// redisData.getData() 取出来的是json数据

        // 4. 检查缓存是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 如果缓存未过期，直接返回店铺数据
            return Result.ok(shop);
        }

        // 5. 缓存已过期，尝试获取分布式锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        if (isLock) {
            // 6. 获取锁成功，提交缓存重建任务到线程池
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 执行缓存重建逻辑
                    this.saveShop2Redis(id, 30L);
                } catch (Exception e) {
                    // 捕获异常并抛出
                    throw new RuntimeException(e);
                } finally {
                    // 无论是否成功，最终释放锁
                    unlock(lockKey);
                }
            });
        }

        // 7. 获取锁失败，返回旧的店铺数据
        return Result.ok(shop);
    }

    // 根据距离查询 店铺
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = shopService.query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end) // 限制查询五个 includeDistance 让查询结果包含距离

        ); // 返回的是一个  GeoResults<RedisGeoCommands.GeoLocation<M>>
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        if (list.size() < from) {
            return Result.ok();
        }
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }


        return Result.ok(shops);
    }

    // 尝试获取分布式锁
    private boolean tryLock(String key) {
        // 使用 Redis 的 setIfAbsent 方法尝试获取锁，设置过期时间为 10 秒
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 返回锁是否获取成功
        return BooleanUtil.isTrue(flag);
    }

    // 释放分布式锁
    public void unlock(String key) {
        // 删除 Redis 中的锁键
        stringRedisTemplate.delete(key);
    }


}
