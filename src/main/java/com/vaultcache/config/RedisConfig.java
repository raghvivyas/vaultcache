package com.vaultcache.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> t = new RedisTemplate<>();
        t.setConnectionFactory(factory);
        t.setKeySerializer(new StringRedisSerializer());
        t.setValueSerializer(new StringRedisSerializer());
        t.setHashKeySerializer(new StringRedisSerializer());
        t.setHashValueSerializer(new StringRedisSerializer());
        t.afterPropertiesSet();
        return t;
    }

    // ── Pre-loaded Lua scripts (loaded once at startup, executed atomically) ──

    /**
     * Fixed Window Lua script.
     * KEYS[1] = rate limit key
     * ARGV[1] = window size in seconds
     * ARGV[2] = max requests per window
     * Returns 1 if allowed, 0 if throttled
     */
    @Bean(name = "fixedWindowScript")
    public DefaultRedisScript<Long> fixedWindowScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local key = KEYS[1]\n" +
            "local window = tonumber(ARGV[1])\n" +
            "local limit  = tonumber(ARGV[2])\n" +
            "local current = redis.call('INCR', key)\n" +
            "if current == 1 then\n" +
            "  redis.call('EXPIRE', key, window)\n" +
            "end\n" +
            "if current > limit then\n" +
            "  return 0\n" +
            "end\n" +
            "return 1\n"
        );
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Sliding Window Log Lua script.
     * KEYS[1] = rate limit key (sorted set)
     * ARGV[1] = current timestamp in milliseconds
     * ARGV[2] = window size in milliseconds
     * ARGV[3] = max requests per window
     * Returns 1 if allowed, 0 if throttled
     */
    @Bean(name = "slidingWindowScript")
    public DefaultRedisScript<Long> slidingWindowScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local key        = KEYS[1]\n" +
            "local now         = tonumber(ARGV[1])\n" +
            "local window      = tonumber(ARGV[2])\n" +
            "local limit       = tonumber(ARGV[3])\n" +
            "local window_start = now - window\n" +
            -- Remove timestamps older than the window
            "redis.call('ZREMRANGEBYSCORE', key, 0, window_start)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count < limit then\n" +
            "  redis.call('ZADD', key, now, now .. '-' .. math.random(100000))\n" +
            "  redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)\n" +
            "  return 1\n" +
            "end\n" +
            "return 0\n"
        );
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Token Bucket Lua script.
     * KEYS[1] = bucket key
     * ARGV[1] = current timestamp in seconds
     * ARGV[2] = bucket capacity (max tokens)
     * ARGV[3] = refill rate (tokens per second)
     * ARGV[4] = tokens to consume (usually 1)
     * Returns 1 if allowed, 0 if throttled
     */
    @Bean(name = "tokenBucketScript")
    public DefaultRedisScript<Long> tokenBucketScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local key         = KEYS[1]\n" +
            "local now         = tonumber(ARGV[1])\n" +
            "local capacity    = tonumber(ARGV[2])\n" +
            "local refill_rate = tonumber(ARGV[3])\n" +
            "local consume     = tonumber(ARGV[4])\n" +
            "local data = redis.call('HMGET', key, 'tokens', 'last_refill')\n" +
            "local tokens      = tonumber(data[1]) or capacity\n" +
            "local last_refill = tonumber(data[2]) or now\n" +
            "local elapsed     = now - last_refill\n" +
            "local new_tokens  = math.min(capacity, tokens + (elapsed * refill_rate))\n" +
            "if new_tokens >= consume then\n" +
            "  redis.call('HMSET', key, 'tokens', new_tokens - consume, 'last_refill', now)\n" +
            "  redis.call('EXPIRE', key, math.ceil(capacity / refill_rate) + 10)\n" +
            "  return 1\n" +
            "end\n" +
            "return 0\n"
        );
        script.setResultType(Long.class);
        return script;
    }
}
