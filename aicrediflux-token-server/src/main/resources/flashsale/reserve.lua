local campaignKey = KEYS[1]
local stockKey = KEYS[2]
local usersKey = KEYS[3]
local reservationKey = KEYS[4]

local userId = ARGV[1]
local requestNo = ARGV[2]
local nowMs = tonumber(ARGV[3])

if redis.call('EXISTS', campaignKey) == 0 or redis.call('EXISTS', stockKey) == 0 then
    return 1
end
if redis.call('HGET', campaignKey, 'status') ~= '1' then
    return 6
end
local startMs = tonumber(redis.call('HGET', campaignKey, 'startMs'))
local endMs = tonumber(redis.call('HGET', campaignKey, 'endMs'))
if nowMs < startMs then return 2 end
if nowMs >= endMs then return 3 end
if tonumber(redis.call('GET', stockKey)) <= 0 then return 4 end
if redis.call('SISMEMBER', usersKey, userId) == 1 then return 5 end

local expireAtMs = endMs + 172800000
redis.call('DECR', stockKey)
redis.call('SADD', usersKey, userId)
redis.call('HSET', reservationKey, 'requestNo', requestNo, 'userId', userId, 'state', 'RESERVED')
redis.call('PEXPIREAT', campaignKey, expireAtMs)
redis.call('PEXPIREAT', stockKey, expireAtMs)
redis.call('PEXPIREAT', usersKey, expireAtMs)
redis.call('PEXPIREAT', reservationKey, expireAtMs)
return 0