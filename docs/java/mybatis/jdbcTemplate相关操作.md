### 1、判断表是否存在

```java
public boolean tableExists(String tableName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = database() AND table_name = ?";
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        return result != null && result > 0;
    }
```

### 2、创建表

```java
public void createTable(String tableName, Map<String, Object> columnMap) {
        if (tableExists(tableName)) {
            return;
        }

        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (");
        sql.append("id bigint(20) NOT NULL PRIMARY KEY AUTO_INCREMENT,");

        // 将 Map 中的键连接起来作为字段定义
        for (String columnName : columnMap.keySet()) {
            if ("id".equals(columnName)) {
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
```

### 3、新增字段

```java
public void appendNewFields(String tableName, Map<String, Object> columnMap) {
        String sql = "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema = database() AND table_name = ?";
        List<String> columns = jdbcTemplate.queryForList(sql, String.class, tableName);

        for (String key : columnMap.keySet()) {
            if (!columns.contains(key)) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + key + " text;");
            }
        }
    }
```

### 4、批量插入数据

```java
public void batchInsert(String tableName, List<Map<String, Object>> datas) {
        Map<String, Object> data = datas.get(0);
        String columns = data.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(","));
        StringBuilder sb = new StringBuilder("INSERT INTO ")
                .append(tableName)
                .append(" (")
                .append(String.join(", ", data.keySet()))
                .append(") VALUES (")
                .append(columns)
                .append(")");
        
        MapSqlParameterSource[] batchValues = new MapSqlParameterSource[datas.size()];
        for (int i = 0; i < datas.size(); i++) {
            batchValues[i] = new MapSqlParameterSource(datas.get(i));
        }
        namedParameterJdbcTemplate.batchUpdate(sb.toString(), batchValues);
    }
```

### 5、批量新增或更新

```java
public void saveOrUpdateBatch(String tableName, List<Map<String, Object>> dataList) {

        Map<String, Object> data = dataList.get(0);

        //占位符
        String columns = data.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(","));

        // 构建更新部分
        String values = data.keySet().stream().map(k -> k + "=VALUES(" + k + ")").collect(Collectors.joining(","));

        StringBuilder sb = new StringBuilder("INSERT INTO ")
                .append(tableName)
                .append(" (")
                .append(String.join(",", data.keySet()))
                .append(") VALUES (")
                .append(columns)
                .append(") ON DUPLICATE KEY UPDATE ")
                .append(values);

        MapSqlParameterSource[] batchParams = new MapSqlParameterSource[dataList.size()];
        for (int i = 0; i < dataList.size(); i++) {
            batchParams[i] = new MapSqlParameterSource(dataList.get(i));
        }
        namedParameterJdbcTemplate.batchUpdate(sb.toString(), batchParams);
    }
```

