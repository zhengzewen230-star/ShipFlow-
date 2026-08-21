package com.shipflow.store.mapper;
import com.shipflow.store.domain.model.Store; import com.shipflow.store.api.model.StoreListItem; import org.apache.ibatis.annotations.*; import java.util.List;
@Mapper public interface StoreMapper {
    Store findById(@Param("tenantId") Long t,@Param("storeId") Long id);
    Store findByIdForUser(@Param("tenantId") Long t,@Param("userId") Long u,@Param("storeId") Long id);
    Store findByCode(@Param("tenantId") Long t,@Param("storeCode") String c);
    Store findByAccount(@Param("tenantId") Long t,@Param("platformCode") String p,@Param("platformAccount") String a);
    List<Store> page(@Param("tenantId") Long t,@Param("status") String s,@Param("platformCode") String p,@Param("offset") int o,@Param("pageSize") int n);
    List<StoreListItem> pageForUser(@Param("tenantId") Long t,@Param("userId") Long u,@Param("storeCode") String c,@Param("storeName") String n,@Param("platformCode") String p,@Param("status") String s,@Param("sortBy") String sort,@Param("sortDirection") String direction,@Param("offset") int o,@Param("pageSize") int size);
    long count(@Param("tenantId") Long t,@Param("status") String s,@Param("platformCode") String p);
    long countForUser(@Param("tenantId") Long t,@Param("userId") Long u,@Param("storeCode") String c,@Param("storeName") String n,@Param("platformCode") String p,@Param("status") String s,@Param("sortBy") String sort,@Param("sortDirection") String direction);
    long countHistoricalOrders(@Param("tenantId") Long t,@Param("storeId") Long id);
    int insert(Store s);
    int update(@Param("tenantId") Long t,@Param("storeId") Long id,@Param("storeName") String n,@Param("platformCode") String p,@Param("platformAccount") String a,@Param("version") long v);
    int updateStatus(@Param("tenantId") Long t,@Param("storeId") Long id,@Param("status") String s,@Param("version") long v);
}
