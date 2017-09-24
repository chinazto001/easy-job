package com.wz.job.common.config;

import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * <p>数据源</p>
 * Created by wangzi on 2017/09/24.
 */
@Configuration
@MapperScan(basePackages = {"com.wz.job.common.mapper"})
public class MybatisConfiguration {
    @Autowired
    private DataSource dataSource;

    @Bean
    public SqlSessionFactoryBean sqlSessionFactory(
            @Value("${mybatis.mapperLocations}") String mapperLocations,
            @Value("${mybatis.typeAliasesPackage}") String typeAliasesPackage) throws Exception {
        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocations));
        sessionFactoryBean.setTypeAliasesPackage(typeAliasesPackage);
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setLazyLoadingEnabled(true);
        sessionFactoryBean.setConfiguration(config);
        return sessionFactoryBean;
    }

    @Bean
    public DataSourceTransactionManager transactionManager() {
        return new DataSourceTransactionManager(dataSource);
    }
}
