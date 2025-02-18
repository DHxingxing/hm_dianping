package com.hmdp.utils;

public class RedisConstant {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_TOKEN = "login:token:";
    public static final Long LOGIN_TOKEN_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    public static final String CACHE_TYPE_KEY = "cache:type:";
    public static final String SECKILL_STOCK_KEY = "secKill:stock:";


}
