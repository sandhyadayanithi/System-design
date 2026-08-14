local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

-- Retrieve current token state from Hash
local data = redis.call('HMGET', key, 'tokens', 'last_refill_time')
local last_tokens = tonumber(data[1])
local last_refill_time = tonumber(data[2])

local current_tokens
if not last_tokens or not last_refill_time then
    -- Initialize bucket: start with full capacity
    current_tokens = capacity
    last_refill_time = now
else
    -- Calculate elapsed time in seconds
    local elapsed = (now - last_refill_time) / 1000.0
    if elapsed < 0 then
        elapsed = 0
    end
    -- Calculate refilled tokens and cap at capacity
    current_tokens = math.min(capacity, last_tokens + (elapsed * refill_rate))
end

local allowed = 0
local remaining_tokens = current_tokens
local wait_time = 0.0

if current_tokens >= requested then
    allowed = 1
    remaining_tokens = current_tokens - requested
    -- Write new state to Redis
    redis.call('HMSET', key, 'tokens', tostring(remaining_tokens), 'last_refill_time', tostring(now))
    -- Auto-expire the rate limit key after 1 hour of inactivity to conserve memory
    redis.call('EXPIRE', key, 3600)
else
    allowed = 0
    remaining_tokens = current_tokens
    -- Time needed to refill enough tokens for the request
    wait_time = (requested - current_tokens) / refill_rate
end

return { tostring(allowed), tostring(remaining_tokens), tostring(wait_time) }
