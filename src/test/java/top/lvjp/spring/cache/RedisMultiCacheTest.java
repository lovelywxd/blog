package top.lvjp.spring.cache;


import com.google.common.collect.Lists;
import javafx.scene.chart.PieChart;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.context.junit4.SpringRunner;
import top.lvjp.common.Data;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootTest
@RunWith(SpringRunner.class)
public class RedisMultiCacheTest {

    private static final Logger log = LoggerFactory.getLogger(RedisMultiCache.class);

    @Autowired
    private RedisCacheManager redisCacheManager;

    @Resource(name = "dataCache")
    private RedisMultiCache dataCache;

    @Autowired
    @Qualifier("dataListCache")
    private RedisMultiCache dataListCache;


    @Test
    public void testCache(){
        List<Data> dataList = new ArrayList<>(5);
        List<Integer> keys = Lists.newArrayList(1,2,3,4,5);
        for (int i = 1; i < 6; i++) {
            dataList.add(new Data(i, String.valueOf(i)));
        }

        Map<Integer, Data> dataMap = dataList.stream().collect(Collectors.toMap(Data::getId, Function.identity()));
        dataCache.putBatch(dataMap);
        dataCache.put(10, new Data(10,"10"));

        System.out.println("10===" + dataCache.get(10).get());
        System.out.println("5===" + dataCache.get(5).get());

        // 测试 null
        dataCache.put(20, null);
        keys.add(20);

        Cache.ValueWrapper wrapper = dataCache.get(20);
        System.out.println(wrapper);

        List<Data> list1 = dataCache.list(keys, this::listData, Data::getId, Data.class);
        System.out.println(list1);

        // 加一个不存在的key
        keys.add(100);

        List<Data> list = dataCache.list(keys, this::listData, Data::getId, Data.class);
        System.out.println(list);
    }

    /**
     * @param keys 注意 这个参数类型 一定要是 Collection 类型
     * @return
     */
    private List<Data> listData(Collection<Integer> keys) {
        List<Data> dataList = new ArrayList<>(keys.size());
        for (Integer key : keys) {
            System.out.println("create data =》 "+key);
            dataList.add(new Data(key, String.valueOf(key)));
        }
        return dataList;
    }


    @Test
    public void testListCache() {
        List<Data> dataList = new ArrayList<>(5);
        List<Integer> keys = Lists.newArrayList(1,2,3,4,5);
        for (int i = 1; i < 6; i++) {
            dataList.add(new Data(i, String.valueOf(i)));
            dataList.add(new Data(i, String.valueOf(i)));
        }

        Map<Integer, List<Data>> dataMap = dataList.stream().collect(Collectors.groupingBy(Data::getId));
        dataListCache.putBatch(dataMap);

        keys.add(100);

        List<Data> list = dataListCache.list(keys, this::listDListata, Data::getId, Data.class, true);
        System.out.println(list);

        System.out.println(dataListCache.get(100).get());

    }

    private List<Data> listDListata(Collection<Integer> keys) {
        List<Data> dataList = new ArrayList<>(keys.size());
        for (Integer key : keys) {
            System.out.println("create data =》 "+key);
            dataList.add(new Data(key, String.valueOf(key)));
            dataList.add(new Data(key, String.valueOf(key)));
        }
        return dataList;
    }
}