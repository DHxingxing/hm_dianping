package com.hmdp.service.impl;

import com.hmdp.controller.VoucherController;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConifg;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Autowired
    private VoucherController voucherController;

    @Resource
    private RedisConifg redisConifg;

    @Override
    public Result seckillOrder(Long voucherId) {

        // 执行lua 脚本
        



        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }

        if (voucher.getStock() < 1) {
            Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
//            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId userId); // 同样的id 来的话 只能让一个人得到锁 其余相同的id的会被锁在外面不允许进来，防止黄牛
//        }

//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = simpleRedisLock.tryLock(1200L);
//        simpleRedisLock.unlock();

        RLock lock = redisConifg.redissonClient().getLock("redission:lock:order:" + userId); // 这里返回的是一个RedissonLock 类

        boolean isLock = lock.tryLock();

        if (!isLock) {
            return Result.fail("只允许下一单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, userId); // 同样的id 来的话 只能让一个人得到锁 其余相同的id的会被锁在外面不允许进来，防止黄牛
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {

//            simpleRedisLock.unlock();

            lock.unlock();
        }
    }

//    @Override
//    public Result seckillOrder(Long voucherId) {
//
//        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//
//        if (voucher.getStock() < 1) {
//            Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()) {
////            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId userId); // 同样的id 来的话 只能让一个人得到锁 其余相同的id的会被锁在外面不允许进来，防止黄牛
////        }
//
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
////        boolean isLock = simpleRedisLock.tryLock(1200L);
////        simpleRedisLock.unlock();
//
//        RLock lock = redisConifg.redissonClient().getLock("redission:lock:order:" + userId); // 这里返回的是一个RedissonLock 类
//
//        boolean isLock = lock.tryLock();
//
//        if (!isLock) {
//            return Result.fail("只允许下一单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId, userId); // 同样的id 来的话 只能让一个人得到锁 其余相同的id的会被锁在外面不允许进来，防止黄牛
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
//
////            simpleRedisLock.unlock();
//
//            lock.unlock();
//        }
//    }


    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {
        // 一人一单 // 这里还是会存在高并发存在的问题

        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        /*
        为什么 query() 查的是 VoucherOrder 表？
        继承了 ServiceImpl：你的 VoucherOrderServiceImpl 类继承了 ServiceImpl<VoucherOrderMapper, VoucherOrder>，
        其中 VoucherOrderMapper 是与 VoucherOrder 表进行映射的 MyBatis 映射器，VoucherOrder 是与数据库表 voucher_order 对应的实体类。
        因此，当你在 VoucherOrderServiceImpl 中调用 query() 时，实际上是针对 voucher_order 表进行操作的。
         */

        if (count >= 1) {
            return Result.fail("你已经购买过了");
        }

        // 扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .gt("stock", 0) // cas 乐观锁
                .eq("voucher_id", voucherId).update();// eq == where 条件

        if (!success) {
            Result.fail("库存不足");
        }


        VoucherOrder voucherOrder = new VoucherOrder();
        long orderID = redisIdWorker.nextID("order");
        voucherOrder.setId(orderID);

        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);
        return Result.ok(orderID);


        // 在这里的时候锁就释放了 其他线程就可以进入了 ，所以这里还是会存在 线程安全问题
    }
}

