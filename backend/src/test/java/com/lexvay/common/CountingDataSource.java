package com.lexvay.common;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Counts connection checkouts so a test can state how many connections a request used. Import
 * {@link Config} on a test class to wrap the application's data source.
 */
public class CountingDataSource extends DelegatingDataSource {

    private final AtomicLong checkouts = new AtomicLong();

    CountingDataSource(DataSource target) {
        super(target);
    }

    public long checkouts() {
        return checkouts.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        checkouts.incrementAndGet();
        return super.getConnection();
    }

    @TestConfiguration
    public static class Config {
        @Bean
        static BeanPostProcessor countingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return beanName.equals("dataSource") ? new CountingDataSource((DataSource) bean) : bean;
                }
            };
        }
    }
}
