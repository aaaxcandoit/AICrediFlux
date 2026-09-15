local stockKey = KEYS[1]
local usersKey = KEYS[2]
local reservationKey = KEYS[3]
local userId = ARGV[1]
local requestNo = ARGV[2]

if redis.call('HGET', reservationKey, 'requestNo') ~= requestNo then return 0 end
if redis.call('HGET', reservationKey, 'state') ~= 'RESERVED' then return 0 end
redis.call('HSET', reservationKey, 'state', 'ROLLED_BACK')
redis.call('INCR', stockKey)
redis.call('SREM', usersKey, userId)
return 1
