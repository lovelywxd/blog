package top.lvjp.spring.cache;

import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lvjp
 * @date 2020/8/8
 */
@Configuration
//@EnableCaching  // 如果需要使用注解形式的缓存则加上该注解
public class RedisCacheConfig extends CachingConfigurerSupport {

    /**
     * 默认缓存存活时间(time to live), 2 小时
     */
    private static final long DEFAULT_TTL_SECONDS = 2 * 60 * 60;

    public static final String DATA_CACHE = "data_cache";
    public static final String DATA_LIST_CACHE = "data_list_cache";

    /*
     * 注册对应的 RedisMultiCache
     */

    @Bean("dataCache")
    public RedisMultiCache dataCache(RedisTemplate redisTemplate, RedisCacheManager cacheManager) {
        // 这里为了方便演示，创建缓存时，清空已存在的缓存数据，
        return new RedisMultiCache(cacheManager.getCache(DATA_CACHE), redisTemplate, true);
    }

    @Bean("dataListCache")
    public RedisMultiCache dataListCache(RedisTemplate redisTemplate, RedisCacheManager cacheManager) {
        return new RedisMultiCache(cacheManager.getCache(DATA_LIST_CACHE), redisTemplate, true);
    }


    /*
     * redis cache 配置
     */

    /**
     * 配置 redisTemplate 使用 fastjson 作为缓存数据的序列化方式
     */
    @Bean("redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);
        redisTemplate.setStringSerializer(StringRedisSerializer.UTF_8);
        redisTemplate.setValueSerializer(new FastJsonRedisSerializer<>(Object.class));

        return redisTemplate;
    }

    // 定制化 json valueSerializer 的功能，这里直接用默认的  new FastJsonRedisSerializer<>(Object.class)
//    private RedisSerializer getValueJsonSerializer() {
//        FastJsonConfig config = new FastJsonConfig();
//
//        config.setCharset(StandardCharsets.UTF_8);
//        // 设置一些序列化的功能，看名字大概就知道什么意思，不设置也可以
//        config.setSerializerFeatures(SerializerFeature.SkipTransientField,
//                SerializerFeature.IgnoreNonFieldGetter,
//                SerializerFeature.WriteEnumUsingToString,
//                SerializerFeature.WriteNullStringAsEmpty,
//                SerializerFeature.WriteNullListAsEmpty);
//        config.setFeatures(Feature.SupportArrayToBean,
//                Feature.SupportNonPublicField);
//
//        FastJsonRedisSerializer<Object> serializer = new FastJsonRedisSerializer<>(Object.class);
//        serializer.setFastJsonConfig(config);
//        return serializer;
//    }


    /**
     * redis 缓存管理器
     * 可以添加自定义缓存配置
     */
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheWriter redisCacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);

        return RedisCacheManager.builder(redisCacheWriter)
                .withInitialCacheConfigurations(initCacheConfig())
                // 设置默认的缓存配置，默认过期时间 2小时
                .cacheDefaults(redisCacheConfig(DEFAULT_TTL_SECONDS))
                .transactionAware()
                .build();
    }

    /**
     * 如果需要指定某个缓存的个性化的配置（如过期时间），可以在map里面添加
     * @return 初始化缓存配置
     */
    private Map<String, RedisCacheConfiguration> initCacheConfig() {
        Map<String, RedisCacheConfiguration> configurationMap = new HashMap<>(4);
        // 指定 data_cache 的过期时间为 半小时
        configurationMap.put(DATA_CACHE, redisCacheConfig(30 * 60));
        return configurationMap;
    }

    /**
     * 设置 redis 缓存的配置
     * 主要设置缓存时间, 和序列化方式
     * @param seconds 指定过期时间 单位：秒
     */
    @SuppressWarnings("unchecked")
    private RedisCacheConfiguration redisCacheConfig(long seconds) {
        return RedisCacheConfiguration.defaultCacheConfig()
                // TODO 注意 这里的 key 和 value 的 serialize 务必和 redisTemplate 保持一致，否则批量操作和单个操作使用不一样的序列化工具会导致读取缓存时报错
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new FastJsonRedisSerializer<>(Object.class)))
                .computePrefixWith(RedisCacheConfig::computeCachePrefix)
                .entryTtl(Duration.ofSeconds(seconds));
    }

    /**
     * 根据缓存名设置对应缓存的key的前缀，推荐使用 程序名+缓存名
     * @param cacheName 缓存名（类似数据库表名）
     */
    public static String computeCachePrefix(String cacheName) {
        return "appName-" + cacheName + "::";
    }
}
