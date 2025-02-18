package com.hmdp.service.impl;

import com.hmdp.controller.VoucherController;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConifg;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstant.SECKILL_ORDER_ID;

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


    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024); // 阻塞队列：当一个线程尝试从这个队列中获取元素的时候，如果没有元素就会阻塞

    private final ExecutorService SEKILL_ORDER_EXCUTOR = Executors.newSingleThreadExecutor();
    @Autowired
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @PostConstruct //  这个注解的作用是 创建类的时候自动 执行这个方法
    private void init() {
        SEKILL_ORDER_EXCUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder voucherOrder = orderTasks.take(); // 检索并删除此队列的头部，必要时等待，直到元素可用为止。
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常 " + e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {

        // 获取用户信息 因为是多线程了 不能从UserHolder中取了
        Long userId = voucherOrder.getUserId();

        RLock lock = redisConifg.redissonClient().getLock("redission:lock:order:" + userId); // 这里返回的是一个RedissonLock 类

        boolean isLock = lock.tryLock();

        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }

        try {
            proxy.createVoucherOrder(voucherOrder); // 同样的id 来的话 只能让一个人得到锁 其余相同的id的会被锁在外面不允许进来，防止黄牛
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    } // 类一加载，这里的 静态资源就初始化了


    private IVoucherOrderService proxy;

    @Override
    public Result seckillOrder(Long voucherId) {
        Long userid = UserHolder.getUser().getId();
        String userID = userid.toString();
        /*
        在 Lua 脚本中返回的数字值（如 0 或 1）在 Java 中通常会被映射为 Long 类型。这是因为 Lua 中的数字默认是双精度浮点数（double），
        但在 Java 中，尤其是通过一些常见的 Lua 与 Java 的桥接库（如 LuaJ），Lua 的数字值通常会被转换为 Long 类型。
         */
        // 执行lua 脚本
        Long execute = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userID);
        int r = execute.intValue();
        if (execute != 0) {
            System.out.println(execute == 1 ? "库存不足" : "不能重复下单");
            return Result.fail(execute == 1 ? "库存不足" : "不能重复下单");
        }

        long orderID = redisIdWorker.nextID(SECKILL_ORDER_ID + voucherId);

        // todo 保存阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userid);
        voucherOrder.setId(orderID);


        orderTasks.add(voucherOrder); //  封装好订单信息后，放入阻塞队列

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderID);
    }
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

//        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
//            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId userId); // 同样的id 来的话 只能让一个人得到锁 其余相同的id的会被锁在外面不允许进来，防止黄牛
//        }

//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = simpleRedisLock.tryLock(1200L);
//        simpleRedisLock.unlock();


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
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单 // 这里还是会存在高并发存在的问题
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();

        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        /*
        为什么 query() 查的是 VoucherOrder 表？
        继承了 ServiceImpl：你的 VoucherOrderServiceImpl 类继承了 ServiceImpl<VoucherOrderMapper, VoucherOrder>，
        其中 VoucherOrderMapper 是与 VoucherOrder 表进行映射的 MyBatis 映射器，VoucherOrder 是与数据库表 voucher_order 对应的实体类。
        因此，当你在 VoucherOrderServiceImpl 中调用 query() 时，实际上是针对 voucher_order 表进行操作的。
         */

        // 扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .gt("stock", 0) // cas 乐观锁
                .eq("voucher_id", voucherId).update();// eq == where 条件

        if (!success) {
            log.error("库存不足");
        }

        save(voucherOrder);

        // 在这里的时候锁就释放了 其他线程就可以进入了 ，所以这里还是会存在 线程安全问题
    }

//    @Transactional
//    public Result createVoucherOrder(Long voucherId, Long userId) {
//        // 一人一单 // 这里还是会存在高并发存在的问题
//
//        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        /*
//        为什么 query() 查的是 VoucherOrder 表？
//        继承了 ServiceImpl：你的 VoucherOrderServiceImpl 类继承了 ServiceImpl<VoucherOrderMapper, VoucherOrder>，
//        其中 VoucherOrderMapper 是与 VoucherOrder 表进行映射的 MyBatis 映射器，VoucherOrder 是与数据库表 voucher_order 对应的实体类。
//        因此，当你在 VoucherOrderServiceImpl 中调用 query() 时，实际上是针对 voucher_order 表进行操作的。
//         */
//
//        if (count >= 1) {
//            return Result.fail("你已经购买过了");
//        }
//
//        // 扣减库存
//        boolean success = iSeckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .gt("stock", 0) // cas 乐观锁
//                .eq("voucher_id", voucherId).update();// eq == where 条件
//
//        if (!success) {
//            Result.fail("库存不足");
//        }
//
//
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderID = redisIdWorker.nextID("order");
//        voucherOrder.setId(orderID);
//
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//
//        save(voucherOrder);
//        return Result.ok(orderID);
//
//
//        // 在这里的时候锁就释放了 其他线程就可以进入了 ，所以这里还是会存在 线程安全问题
//    }
}

