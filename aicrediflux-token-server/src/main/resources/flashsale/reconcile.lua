local campaignKey = KEYS[1]
local stockKey = KEYS[2]
local usersKey = KEYS[3]
local status = ARGV[1]
local startMs = ARGV[2]
local endMs = ARGV[3]
local stock = ARGV[4]
local expireAtMs = tonumber(ARGV[5])
redis.call('HSET', campaignKey, 'status', status, 'startMs', startMs, 'endMs', endMs)
redis.call('SET', stockKey, stock)
redis.call('DEL', usersKey)
redis.call('SADD', usersKey, '__ready__')
for i = 6, #ARGV do redis.call('SADD', usersKey, ARGV[i]) end
redis.call('PEXPIREAT', campaignKey, expireAtMs)
redis.call('PEXPIREAT', stockKey, expireAtMs)
redis.call('PEXPIREAT', usersKey, expireAtMs)
return 1