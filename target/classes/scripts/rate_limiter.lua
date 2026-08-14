-- src/main/resources/scripts/rate_limiter.lua
-- ARGV[1]: apiKey (the key for the sorted set)
-- ARGV[2]: currentTime (current unix timestamp in milliseconds)
-- ARGV[3]: windowSize (e.g., 60000 for 1 minute)
-- ARGV[4]: limit (e.g., 10 for 10 requests)

local key = ARGV[1]
local current_time = tonumber(ARGV[2])
local window = tonumber(ARGV[3])
local limit = tonumber(ARGV[4])

-- The start of the time window
local window_start = current_time - window

-- Remove all timestamps that are outside the current window
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- Get the current number of requests in the window
local request_count = redis.call('ZCARD', key)

-- If the count is below the limit, add the new request and allow it
if request_count < limit then
  -- Append sequence counter to member so requests in the exact same millisecond remain unique
  local seq = redis.call('INCR', key .. ':seq')
  redis.call('ZADD', key, current_time, current_time .. ':' .. seq)
  -- Set an expiration on the key to clean it up if it's not used anymore
  redis.call('EXPIRE', key, math.ceil(window / 1000)) -- expire in seconds
  redis.call('EXPIRE', key .. ':seq', math.ceil(window / 1000))
  return 1 -- 1 means allowed
else
  return 0 -- 0 means denied
end
