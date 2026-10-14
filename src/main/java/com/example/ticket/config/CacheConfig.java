package com.example.ticket.config;

import com.example.ticket.domain.Ticket;
import com.example.ticket.service.TicketCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
public class CacheConfig {
    @Bean @Profile("!mysql")
    TicketCache noCache() {
        return new TicketCache() {
            public Ticket get(long id) { return null; }
            public void put(Ticket ticket) {}
            public void evict(long id) {}
        };
    }
    @Bean @Profile("mysql")
    TicketCache redisCache(StringRedisTemplate redis, ObjectMapper json) {
        return new TicketCache() {
            private final Logger log = LoggerFactory.getLogger(TicketCache.class);
            public Ticket get(long id) {
                try {
                    String value = redis.opsForValue().get("ticket:detail:" + id);
                    return value == null ? null : json.readValue(value, Ticket.class);
                } catch (Exception e) {
                    log.warn("Redis 读取失败，回源数据库：{}", e.getClass().getSimpleName());
                    return null;
                }
            }
            public void evict(long id) {
                try { redis.delete("ticket:detail:" + id); }
                catch (Exception e) { log.warn("Redis 删除失败，依赖 TTL 收敛：{}", e.getClass().getSimpleName()); }
            }
            public void put(Ticket ticket) {
                try {
                    // 随机过期时间降低同一时刻大量缓存失效的风险
                    redis.opsForValue().set("ticket:detail:" + ticket.getId(), json.writeValueAsString(ticket),
                        Duration.ofSeconds(30 + ThreadLocalRandom.current().nextInt(15)));
                } catch (Exception e) {
                    log.warn("Redis 写入失败：{}", e.getClass().getSimpleName());
                }
            }
        };
    }
}
