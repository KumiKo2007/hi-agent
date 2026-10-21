package org.xhy.interfaces.api.portal.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.repository.review.JdbcPrInputStore;
import java.sql.Connection;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 显式配置的独立 PostgreSQL 中，通过公开 HTTP 验证快照重建与所有者隔离。 */
@EnabledIfEnvironmentVariable(named = "PR_INPUT_POSTGRES_URL", matches = ".+")
class PrInputPostgresHttpIntegrationTest {
    @Test
    void reloadsFixedSnapshotAfterReconstructingPersistenceAndController() throws Exception {
        String schema = "pr_input_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource admin = new DriverManagerDataSource(System.getenv("PR_INPUT_POSTGRES_URL"),
                System.getenv("PR_INPUT_POSTGRES_USER"), System.getenv("PR_INPUT_POSTGRES_PASSWORD"));
        JdbcTemplate control = new JdbcTemplate(admin);
        control.execute("CREATE SCHEMA " + schema);
        String url = System.getenv("PR_INPUT_POSTGRES_URL");
        DriverManagerDataSource isolated = new DriverManagerDataSource(
                url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                System.getenv("PR_INPUT_POSTGRES_USER"), System.getenv("PR_INPUT_POSTGRES_PASSWORD"));
        try {
            try (Connection connection = isolated.getConnection()) {
                ScriptUtils.executeSqlScript(connection,
                        new ClassPathResource("db/migration/V20261020020__create_pr_input_snapshots.sql"));
            }
            PrInputHttpTest fixture = new PrInputHttpTest();
            UserContext.setCurrentUserId("owner");
            String id = fixture
                    .submit(fixture.api(new JdbcPrInputStore(new JdbcTemplate(isolated), new ObjectMapper())),
                            "https://github.com/alice/repo/pull/42")
                    .path("id").asText();
            var restarted = fixture.api(new JdbcPrInputStore(new JdbcTemplate(isolated), new ObjectMapper()));
            restarted.perform(get("/reviews/inputs/" + id)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.headSha").value(PrInputHttpTest.HEAD));
            UserContext.setCurrentUserId("other");
            restarted.perform(get("/reviews/inputs/" + id)).andExpect(status().isBadRequest());
        } finally {
            UserContext.clear();
            control.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
