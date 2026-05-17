package beauty.beauty.config;

import org.h2gis.functions.factory.H2GISFunctions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * H2GIS spatial functions를 Hibernate DDL 생성 전에 로드하는 테스트 설정.
 * Salon 엔티티의 POINT 타입이 H2에서 동작하려면 H2GIS 함수 등록이 필요하다.
 */
@TestConfiguration
public class H2GISTestConfig {

    @Bean
    public H2GISInitializer h2GISInitializer(DataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            H2GISFunctions.load(conn);
        }
        return new H2GISInitializer();
    }

    public static class H2GISInitializer {
    }
}
