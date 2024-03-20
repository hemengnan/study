package com.shudao.sgccpolymericcollect.collect.service;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Maps;
import com.shudao.sgccpolymericcollect.collect.enums.EnumLczxCode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.vavr.API.*;

@Service
@RequiredArgsConstructor
public class DatabaseService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public boolean tableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = database() AND table_name = ?";
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return result != null && result > 0;
    }

    public void createTable(String tableName, Map<String, Object> columnMap, String uniqueKey) {
        if (tableExists(tableName)) {
            return;
        }

        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (");
        sql.append("id bigint(20) NOT NULL PRIMARY KEY AUTO_INCREMENT,");
        if (StringUtils.isNotBlank(uniqueKey)) {
            sql.append(uniqueKey).append(" varchar(50) NOT NULL UNION KEY,");
        }

        // 将 Map 中的键连接起来作为字段定义
        for (String columnName : columnMap.keySet()) {
            if ("id".equals(columnName)) {
                continue;
            }
            if(StringUtils.isNotBlank(uniqueKey) && columnName.equals(uniqueKey)){
                continue;
            }
            sql.append(columnName).append(" text, ");
        }

        // 去掉最后一个逗号
        sql.delete(sql.length() - 2, sql.length());

        sql.append(");");

        // 执行 SQL 语句
        jdbcTemplate.execute(sql.toString());
    }

    public void appendNewFields(String tableName, Map<String, Object> columnMap) {
        List<String> columns = getTableColumns(tableName);
        for (String key : columnMap.keySet()) {
            if (!columns.contains(key)) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN `" + key + "` text;");
            }
        }
    }

    public List<String> getTableColumns(String tableName) {
        String sql = "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema = database() AND table_name = ?";
        return jdbcTemplate.queryForList(sql, String.class, tableName);
    }


    public void batchInsertAndAppendColumn(String tableName, List<Map<String, Object>> datas) {
        for (Map<String, Object> item : datas) {
            appendNewFields(tableName, item);

            String keys = item.keySet().stream().map(k -> "`" + k + "`").collect(Collectors.joining(","));
            String columns = item.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(","));
            StringBuilder sb = new StringBuilder("INSERT INTO ")
                    .append(tableName)
                    .append(" (")
                    .append(keys)
                    .append(") VALUES (")
                    .append(columns)
                    .append(")");

            MapSqlParameterSource[] batchValues = new MapSqlParameterSource[1];
            batchValues[0] = new MapSqlParameterSource(item);
            namedParameterJdbcTemplate.batchUpdate(sb.toString(), batchValues);
        }

    }

    public void batchInsert(String tableName, List<Map<String, Object>> datas) {
        Map<String, Object> data = datas.get(0);
        String keys = data.keySet().stream().map(k -> "`" + k + "`").collect(Collectors.joining(","));
        String columns = data.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(","));
        StringBuilder sb = new StringBuilder("INSERT INTO ")
                .append(tableName)
                .append(" (")
                .append(keys)
                .append(") VALUES (")
                .append(columns)
                .append(")");

        MapSqlParameterSource[] batchValues = new MapSqlParameterSource[datas.size()];
        for (int i = 0; i < datas.size(); i++) {
            batchValues[i] = new MapSqlParameterSource(datas.get(i));
        }
        namedParameterJdbcTemplate.batchUpdate(sb.toString(), batchValues);
    }

    public void saveOrUpdateBatch(String tableName, List<Map<String, Object>> dataList) {
        if (CollUtil.isEmpty(dataList)) {
            return;
        }

        // 确定需要的键集合
        Set<String> keySet = dataList.stream()
                .flatMap(map -> map.keySet().stream())
                .collect(Collectors.toSet());

        // 遍历列表，补充缺失的键
        dataList.forEach(item -> keySet.forEach(key -> item.putIfAbsent(key, null)));

        // 列名
        String keys = keySet.stream().map(k -> "`" + k + "`").collect(Collectors.joining(","));

        //值占位符
        String columns = keySet.stream().map(k -> ":" + k).collect(Collectors.joining(","));

        // 构建更新部分
        String values = keySet.stream().map(k -> k + "=VALUES(" + k + ")").collect(Collectors.joining(","));


        StringBuilder sb = new StringBuilder("INSERT INTO ")
                .append(tableName)
                .append(" (")
                .append(keys)
                .append(") VALUES (")
                .append(columns)
                .append(") ON DUPLICATE KEY UPDATE ")
                .append(values);

        int pageNum = 0;
        int pageSize = 1000;
        while ((pageNum * pageSize) < dataList.size()) {
            List<Map<String, Object>> saveList = dataList.stream()
                    .skip((long) pageNum * pageSize)
                    .limit(pageSize)
                    .collect(Collectors.toList());

            MapSqlParameterSource[] batchParams = new MapSqlParameterSource[saveList.size()];
            for (int i = 0; i < saveList.size(); i++) {
                batchParams[i] = new MapSqlParameterSource(saveList.get(i));
            }
            namedParameterJdbcTemplate.batchUpdate(sb.toString(), batchParams);
            pageNum++;
        }
    }
}
