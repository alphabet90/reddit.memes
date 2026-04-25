package com.memes.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig {

    private final ObjectMapper objectMapper;

    public static final String CACHE_STATS      = "stats";
    public static final String CACHE_CATEGORIES = "categories";
    public static final String CACHE_MEME_LIST  = "meme-list";
    public static final String CACHE_MEME       = "meme";
    public static final String CACHE_SEARCH     = "search";

    private GenericJackson2JsonRedisSerializer redisSerializer() {
        ObjectMapper redisMapper = objectMapper.copy()
            .activateDefaultTypingAsProperty(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                "@class");
        return new GenericJackson2JsonRedisSerializer(redisMapper);
    }

    private RedisCacheConfiguration cacheConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl)
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer()))
            .disableCachingNullValues();
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        Map<String, RedisCacheConfiguration> configs = Map.of(
            CACHE_STATS,      cacheConfig(Duration.ofMinutes(10)),
            CACHE_CATEGORIES, cacheConfig(Duration.ofMinutes(30)),
            CACHE_MEME_LIST,  cacheConfig(Duration.ofMinutes(15)),
            CACHE_MEME,       cacheConfig(Duration.ofHours(1)),
            CACHE_SEARCH,     cacheConfig(Duration.ofMinutes(5))
        );
        return RedisCacheManager.builder(factory)
            .cacheDefaults(cacheConfig(Duration.ofMinutes(10)))
            .withInitialCacheConfigurations(configs)
            .build();
    }
}
