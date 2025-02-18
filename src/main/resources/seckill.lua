--- 优惠卷的id 用户id

local voucherID = ARGV[1];
local userID = ARGV[2];

--- 库存key

local stockKey = "secKill:stock:" .. voucherID;
local orderKey = "secKill:order:" .. voucherID;

if tonumber(redis.call('get', stockKey) <= 0) then
    return 1
end

--- 用户是否下过单

if (redis.call('sismember', orderKey, userID) == 1) then
    return 2 -- 说明是重复下单
end

-- 扣库存

redis.call('incrby', stockKey, -1)

redis.call('sadd', orderKey, userID)
