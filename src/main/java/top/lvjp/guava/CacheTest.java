package top.lvjp.guava;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.sun.jdi.PathSearchingVirtualMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.lvjp.common.Data;

import java.security.Key;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author lvjp
 * @date 2020/8/6
 */
public class CacheTest {

    private static final Logger log = LoggerFactory.getLogger(CacheTest.class);

    private Cache<Integer, Data> dataCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(6))
            .concurrencyLevel(4)
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(10240)
            .initialCapacity(2048)
            .build();

    /**
     * 可以自动加载数据的缓存
     */


    private LoadingCache<Integer, Data> dataLoadingCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .initialCapacity(1024)
            .maximumSize(10240)
            .build(new CacheLoader<Integer, Data>() {
                @Override
                public Data load(Integer key) throws Exception {
                    return getDataById(key);
                }
                @Override
                public Map<Integer, Data> loadAll(Iterable<? extends Integer> keys) throws Exception {
                    List<Integer> ids = Lists.newArrayList(keys.iterator());
                    List<Data> dataList = listDataByIds(ids);
                    return dataList.stream().collect(Collectors.toMap(Data::getId, Function.identity()));
                }
            });

    private Data getDataById(Integer id) {
        return new Data(id, id.toString());
    }

    private List<Data> listDataByIds(List<Integer> ids) {
        List<Data> datas = new ArrayList<>(ids.size());
        ids.forEach(i -> datas.add(new Data(i, i.toString())));
        return datas;
    }

    private Data getDataByIdFromCache(Integer id) {
        try {
            return dataCache.get(id, () -> getDataById(id));
        } catch (ExecutionException e) {
            log.error("获取缓存异常", e);
        }
        return getDataById(id);
    }

    private List<Data> listDataFromCache(List<Integer> ids) {
        try {
            ImmutableMap<Integer, Data> allData = dataLoadingCache.getAll(ids);
            return new ArrayList<>(allData.values());
        } catch (ExecutionException e) {
            e.printStackTrace();
            log.error("批量获取缓存异常", e);
        }
        return listDataByIds(ids);
    }

    private void deleteById(Integer id) {
        // delete from db
        dataCache.invalidate(id);
    }

    private void updateData(Data data) {
        // update data into db

        // LoadingCache 可以刷新缓存
        dataLoadingCache.refresh(data.getId());

        // Cache 只能删除缓存
        dataCache.invalidate(data.getId());
    }

    private void addData(Data data) {
        // Cache 需要手动存入
        dataCache.put(data.getId(), data);
    }


    public static void main(String[] args) {

    }
}


