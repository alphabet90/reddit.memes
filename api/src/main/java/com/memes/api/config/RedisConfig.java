package com.memes.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
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
public class RedisConfig {

    // Cache names carry a "-v2" suffix so any V1-shaped cache entries left over
    // from a previous deploy are ignored after the V2 schema migration. The
    // Jackson-typed serialization in V1 is incompatible with the new DTO shape;
    // bumping the suffix is cheaper than orchestrating a Redis FLUSHDB.
    public static final String CACHE_STATS      = "stats-v2";
    public static final String CACHE_CATEGORIES = "categories-v2";
    public static final String CACHE_MEME_LIST  = "meme-list-v2";
    public static final String CACHE_MEME       = "meme-v2";
    public static final String CACHE_SEARCH     = "search-v2";

    // Fresh, isolated ObjectMapper — never copied from Spring's primary bean.
    // ObjectMapper.copy() shares _typeFactory with the parent, which causes the
    // type resolver set via activateDefaultTypingAsProperty to be ignored during
    // deserialization, producing MismatchedInputException on START_OBJECT tokens.
    private static final GenericJackson2JsonRedisSerializer SERIALIZER = buildSerializer();

    private static GenericJackson2JsonRedisSerializer buildSerializer() {
        ObjectMapper redisMapper = new ObjectMapper()
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
                RedisSerializationContext.SerializationPair.fromSerializer(SERIALIZER))
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
