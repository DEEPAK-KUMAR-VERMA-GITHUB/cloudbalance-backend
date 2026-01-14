package com.cloudkeeper.cloudbalance_backend.repository.snowflake;

import com.snowflake.snowpark_java.Row;
import com.snowflake.snowpark_java.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SnowflakeRepository {

    private final Session snowpark;

    public List<Row> executeQuery(String sql) {
        return Arrays.asList(snowpark.sql(sql).collect());
    }
}
