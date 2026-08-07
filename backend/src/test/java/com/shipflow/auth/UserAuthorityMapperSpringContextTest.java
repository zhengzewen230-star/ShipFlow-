package com.shipflow.auth;

import com.shipflow.auth.mapper.UserAuthorityMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies production MyBatis auto-configuration loads and invokes the authority mapper statement. */
@SpringBootTest
@ActiveProfiles("test")
@Import(UserAuthorityMapperSpringContextTest.MapperDataSourceConfiguration.class)
class UserAuthorityMapperSpringContextTest {

    @Autowired
    private UserAuthorityMapper mapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void productionMapperLocationLoadsAndMappedMethodCanBeInvoked() throws Exception {
        assertThat(sqlSessionFactory.getConfiguration().hasStatement(
                UserAuthorityMapper.class.getName() + ".findActiveAuthorities")).isTrue();

        assertThat(mapper.findActiveAuthorities(2L, 1L, "TENANT")).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MapperDataSourceConfiguration {

        @Bean
        @Primary
        DataSource mapperTestDataSource() throws Exception {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);
            ResultSetMetaData metadata = mock(ResultSetMetaData.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.execute()).thenReturn(true);
            when(statement.getResultSet()).thenReturn(resultSet);
            when(resultSet.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnCount()).thenReturn(0);
            when(resultSet.next()).thenReturn(false);
            return dataSource;
        }
    }
}
