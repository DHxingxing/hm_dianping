----- 优惠卷的id 用户id
--
--local voucherID = ARGV[1];
--local userID = ARGV[2];
--
--local orderID = ARGV[3]
--
----- 库存key
--
--local stockKey = "secKill:stock:" .. voucherID;
--local orderKey = "secKill:order:" .. voucherID;
--
--
--
--if (tonumber(redis.call('get', stockKey)) <= 0) then
--    return 1 -- 库存不足
--end
--
----- 用户是否下过单
--
--if (redis.call('sismember', orderKey, userID) == 1) then
--    return 2 -- 说明是重复下单
--end
--
---- 扣库存
--
--redis.call('incrby', stockKey, -1)
--
--redis.call('sadd', orderKey, userID)
--
---- 发送消息到队列之中
--local id = orderID
--
--redis.call('xadd', 'streams.orders', '*', 'userId', userID, 'voucherID', voucherID, 'ID', id)
--
--return 0


-- 获取参数
local voucherID = ARGV[1];
local userID = ARGV[2];
local orderID = ARGV[3];

-- 库存 key
local stockKey = "secKill:stock:" .. voucherID;
local orderKey = "secKill:order:" .. voucherID;

-- 判断库存
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1 -- 库存不足
end

-- 判断用户是否已下单
if (redis.call('sismember', orderKey, userID) == 1) then
    return 2 -- 说明是重复下单
end

-- 扣减库存
redis.call('incrby', stockKey, -1)

-- 记录用户下单
redis.call('sadd', orderKey, userID)

return 0  -- 订单处理成功
