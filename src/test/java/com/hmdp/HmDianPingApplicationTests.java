package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource

    private ShopServiceImpl shopService;

    @Test

    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L,30L);
    }

    @Resource
    private RedisIdWorker redisIdWorker;


    @Test
    void nextID(){
        long l = redisIdWorker.nextID("dhx");
        System.out.println(l);
    }

}
