package top.lvjp.spring.cache;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 对 spring cache （redis实现） 进行了一层封装，主要包括以下两个
 * <ul>
 *     <li>对默认的Cache方法进行的一层封装，主要进行了try-cache处理，因为不希望因为读取缓存失败导致整个流程报错</li>
 *     <li>添加了批量查询和存入缓存的操作</li>
 * </ul>
 *
 * @author lvjp
 * @date 2020/8/8
 */
public class RedisMultiCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(RedisMultiCache.class);

    /**
     * spring redis cache,
     */
    private Cache cache;

    private RedisTemplate redisTemplate;

    /**
     * 默认不清除原有缓存
     */
    public RedisMultiCache(Cache cache, RedisTemplate redisTemplate) {
        this(cache, redisTemplate, false);
    }

    /**
     * @param cache spring cache
     * @param redisTemplate 用于进行缓存的批量操作
     * @param clearExist 是否初始化缓存（清除redis中已经存在的缓存数据）
     */
    public RedisMultiCache(Cache cache, RedisTemplate redisTemplate, boolean clearExist) {
        Objects.requireNonNull(cache, "cache required not null");
        Objects.requireNonNull(redisTemplate, "redisTemplate required not null");
        this.cache = cache;
        this.redisTemplate = redisTemplate;
        // 创建缓存前, 是否初始化缓存，清除原有的
        if (clearExist) {
            cache.clear();
        }
    }

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public Object getNativeCache() {
        return cache.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        try {
            return cache.get(key);
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
        }
        return null;
    }

    @Override
    public <T> T get(Object key, Class<T> aClass) {
        try {
            return cache.get(key, aClass);
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
        }
        return null;
    }

    /**
     * 获取不为空的缓存，如果缓存中存在为null，则使用 valueLoader 重新加载，并将结果存入缓存中
     * @param key 缓存数据的key
     * @param valueLoader 加载缓存的方法
     * @return {@code null} if valueLoader returned null
     * @param <T> 缓存数据类型
     */
    public <T> T getNonNull(Object key, Callable<T> valueLoader) {
        T value;
        try {
            ValueWrapper wrapper = cache.get(key);
            if (wrapper == null || (value = (T) wrapper.get()) == null) {
                value = valueLoader.call();
                cache.put(key, value);
            }
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
            try {
                return valueLoader.call();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return value;
    }

    /**
     * 如果缓存中存储的为null，则直接返回null
     * 如果 valueLoader 执行时抛出异常，则使用 RuntimeException 继续抛出，调用方自己处理
     * @return  {@code null} if cached null
     */
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        T value;
        try {
            value = cache.get(key, valueLoader);
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
            try {
                return valueLoader.call();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return value;
    }

    @Override
    public void put(Object key, Object value) {
        try {
            cache.put(key, value);
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        try {
            return cache.putIfAbsent(key, value);
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
        }
        return null;
    }

    @Override
    public void evict(Object key) {
        try {
            cache.evict(key);
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
        }
    }

    @Override
    public void clear() {
        try {
            cache.clear();
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
        }
    }

    /**
     * 批量读取缓存，默认 key-value 为一对一的关系
     * @see #list(List, Function, Function, Class, boolean)
     */
    public <K, V> List<V> list(List<K> keys, Function<Collection<K>, Collection<V>> valueLoader, Function<V, K> keyMapper, Class<V> vClass) {
        return list(keys, valueLoader, keyMapper, vClass, false);
    }

    /**
     * 批量获取缓存数据, 如不存在则通过 {@code valueLoader} 获取数据, 并存入缓存中
     * 如果缓存中存在 null，则视为不存在，仍然通过 valueLoader 加载，如需要防止缓存穿透，建议存入空对象，而非 null
     *
     * @param keys        key
     * @param valueLoader 数据加载器
     * @param keyMapper   根据value获取key 映射器
     * @param vClass      返回数据类型
     * @param isListValue value是否为list类型，即一个key对应一个List&lt;V&gt;
     * @param <K>         key 的类型
     * @param <V>         value 的类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <K, V> List<V> list(List<K> keys, Function<Collection<K>, Collection<V>> valueLoader, Function<V, K> keyMapper, Class<V> vClass, boolean isListValue) {
        List list0 = Collections.emptyList();
        try {
            list0 = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer keySerializer = redisTemplate.getKeySerializer();
                for (K k : keys) {
                    byte[] key = keySerializer.serialize(createCacheKey(k));
                    if (key != null) {
                        connection.get(key);
                    } else {
                        log.warn("RedisMultiCache 批量操作序列化失败， key={}", k);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
        }

        int size = keys.size();
        // 缓存不存在的key
        List<K> nkeys = new ArrayList<>(size);
        List<V> values = new ArrayList<>(size);
        if (CollectionUtils.isEmpty(list0)) {
            nkeys.addAll(keys);
        } else {
            for (int i = 0; i < list0.size(); i++) {
                Object o = list0.get(i);
                if (o == null) {
                    nkeys.add(keys.get(i));
                } else {
                    // redis中存储的是 JsonObject 或 JsonArray 对象, RedisConfig 中配置的
                    if (o instanceof JSONArray) {
                        values.addAll(((JSONArray) o).toJavaList(vClass));
                    } else if (o instanceof JSONObject) {
                        values.add(JSONObject.toJavaObject((JSONObject) o, vClass));
                    }
                }
            }
        }

        if (!CollectionUtils.isEmpty(nkeys)) {
            Collection<V> nValue = valueLoader.apply(nkeys);
            Map kvMap;
            if (isListValue) {
                kvMap = nValue.stream().filter(Objects::nonNull).collect(Collectors.groupingBy(keyMapper));
            } else {
                kvMap = nValue.stream().filter(Objects::nonNull).collect(Collectors.toMap(keyMapper, Function.identity()));
            }
            putBatch(kvMap);
            values.addAll(nValue);
        }
        return values;
    }

    /**
     * 批量存入缓存
     * @param map 需要存入的数据
     * @param <K> 数据的 key 的类型
     * @param <V> 数据的 value 的类型
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <K, V> void putBatch(Map<K, V> map) {
        if (CollectionUtils.isEmpty(map)) {
            return;
        }

        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer keySerializer = redisTemplate.getKeySerializer();
                RedisSerializer valueSerializer = redisTemplate.getValueSerializer();
                for (Map.Entry<K, V> entry : map.entrySet()) {
                    byte[] key = keySerializer.serialize(createCacheKey(entry.getKey()));
                    byte[] value = valueSerializer.serialize(entry.getValue());
                    if (key != null && value != null) {
                        connection.set(key, value);
                    } else {
                        log.warn("RedisMultiCache 批量操作序列化失败， entry={}", entry);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.error("RedisMultiCache 异常", e);
        }
    }

    /**
     * @param key 缓存数据的key
     * @param <K> key 的类型
     * @return 缓存数据在 redis 中的 key
     */
    private <K> String createCacheKey(K key) {
        return RedisCacheConfig.computeCachePrefix(getName())+ key.toString();
    }
}

