package org.xhy.interfaces.api.portal.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.teaching.service.TeachingOcrAppService;
import org.xhy.infrastructure.repository.teaching.JdbcTeachingOcrStore;
import org.xhy.infrastructure.auth.UserContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 页缺口和核对记录通过公开HTTP观察，使用真实PostgreSQL。 */
@EnabledIfEnvironmentVariable(named = "TEACHING_INTEGRATION", matches = "true")
class TeachingOcrHttpTest {
    JdbcTemplate jdbc;
    MockMvc api;
    private String schema;
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @BeforeEach
    void prepare() throws Exception {
        var url = System.getenv().getOrDefault("TEACHING_TEST_URL", "jdbc:postgresql://127.0.0.1:55425/teaching_test");
        schema = "ocr_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "teaching-test"))
                .execute("CREATE SCHEMA " + schema);
        var ds = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                "postgres", "teaching-test");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE rag_versions(id text, original_rag_id text, version text, user_id text, search_ready boolean, deleted_at timestamp)");
        jdbc.execute(
                "CREATE TABLE rag_version_files(id text, rag_version_id text, original_file_id text, file_name text, file_type text, file_page_size int, file_path text, deleted_at timestamp)");
        jdbc.execute(
                "CREATE TABLE rag_version_documents(id text, rag_version_id text, rag_version_file_id text, page int, content text, source_locator_type text, source_locator text, coverage_warnings text, deleted_at timestamp)");
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V20261020003__teaching_source_reviews.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V20261020001__teaching_selections.sql"));
        }
        jdbc.update("INSERT INTO rag_versions VALUES('v1','dataset','1.0','owner',true,null)");
        jdbc.update(
                "INSERT INTO rag_version_files VALUES('f1','v1','original','导数扫描.pdf','pdf',3,'http://fixture.local/source.pdf',null)");
        jdbc.update(
                "INSERT INTO rag_version_documents VALUES('d1','v1','f1',0,'导数定义 $f(x)$','FILE_PAGE','{}','[\"OCR识别内容需教师核对\"]',null),('d2','v1','f1',1,null,'FILE_PAGE','{}','[\"此页OCR失败\"]',null),('d3','v1','f1',2,'切线 $x^{2','FILE_PAGE','{}','[\"OCR_FORMULA_GAP: 公式定界符不完整\"]',null)");
        api = controller(null);
        UserContext.setCurrentUserId("owner");
    }
    MockMvc controller(org.dromara.x.file.storage.core.FileStorageService storage) {
        return MockMvcBuilders.standaloneSetup(
                new TeachingOcrController(new TeachingOcrAppService(new JdbcTeachingOcrStore(jdbc, json), storage)),
                new TeachingSelectionController(new org.xhy.application.teaching.service.TeachingSelectionAppService(
                        new org.xhy.infrastructure.repository.teaching.JdbcTeachingSelectionStore(jdbc, json))))
                .setControllerAdvice(new org.xhy.infrastructure.config.GlobalExceptionHandler()).build();
    }
    @AfterEach
    void cleanup() {
        UserContext.clear();
        if (jdbc != null)
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }
    @Test
    void should_keepDuplicatePhysicalPageIncompleteInsteadOfApprovingAmbiguousSources() throws Exception {
        jdbc.update("UPDATE rag_version_documents SET coverage_warnings='[]' WHERE id='d1'");
        jdbc.update("INSERT INTO rag_version_documents VALUES('duplicate','v1','f1',0,'重复页正文','FILE_PAGE','{}','[]',null)");
        api.perform(get("/teaching/ocr/versions/v1/files/f1"))
                .andExpect(jsonPath("$.data.pages[0].status").value("MISSING"));
        api.perform(post("/teaching/selections").contentType("application/json").content(
                """
                {"title":"有重复来源","datasetId":"dataset","versionId":"v1","ranges":[{"fileId":"f1","startPage":1,"endPage":1}]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.data.warnings").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("重复"))));
    }

    @Test
    void should_exposeEveryPhysicalPageAndKeepManualReviewSeparateFromAutomaticChecks() throws Exception {
        api.perform(get("/teaching/ocr/versions/v1/files/f1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageCount").value(3))
                .andExpect(jsonPath("$.data.pages[0].status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.data.pages[1].status").value("MISSING"))
                .andExpect(jsonPath("$.data.pages[2].coverageWarnings[0]").value("OCR_FORMULA_GAP: 公式定界符不完整"));
        api.perform(post("/teaching/ocr/versions/v1/files/f1/pages/1/reviews").contentType("application/json")
                .content("{\"note\":\"已逐项对照原页，定义和公式一致\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review.note").value("已逐项对照原页，定义和公式一致"));
        String request = "{\"title\":\"导数定义\",\"datasetId\":\"dataset\",\"versionId\":\"v1\",\"ranges\":[{\"fileId\":\"f1\",\"startPage\":1,\"endPage\":1}]}";
        var saved = json
                .readTree(api.perform(post("/teaching/selections").contentType("application/json").content(request))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"))
                        .andExpect(jsonPath("$.data.warnings[0]").value("OCR识别内容需教师核对"))
                        .andExpect(jsonPath("$.data.sources[0].review.note").value("已逐项对照原页，定义和公式一致")).andReturn()
                        .getResponse().getContentAsByteArray())
                .path("data");
        String savedId = saved.path("id").asText();
        api = controller(null);
        api.perform(get("/teaching/ocr/versions/v1/files/f1"))
                .andExpect(jsonPath("$.data.pages[0].status").value("HUMAN_REVIEWED"))
                .andExpect(jsonPath("$.data.pages[0].coverageWarnings[0]").value("OCR识别内容需教师核对"));
        api.perform(post("/teaching/ocr/versions/v1/files/f1/pages/2/reviews").contentType("application/json")
                .content("{\"note\":\"直接略过\"}")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE rag_version_documents SET content='变化后内容' WHERE id='d1'");
        api.perform(get("/teaching/ocr/versions/v1/files/f1"))
                .andExpect(jsonPath("$.data.pages[0].status").value("REVIEW_REQUIRED"));
        api.perform(get("/teaching/selections/" + savedId))
                .andExpect(jsonPath("$.data.sources[0].content").value("导数定义 $f(x)$"))
                .andExpect(jsonPath("$.data.sources[0].review.note").value("已逐项对照原页，定义和公式一致"));
        api.perform(post("/teaching/ocr/versions/v1/files/f1/pages/3/reviews").contentType("application/json")
                .content("{\"note\":\"无法补全公式\"}")).andExpect(status().isBadRequest());
        UserContext.setCurrentUserId("other");
        api.perform(get("/teaching/ocr/versions/v1/files/f1")).andExpect(status().isBadRequest());
        api.perform(post("/teaching/ocr/versions/v1/files/f1/pages/1/reviews").contentType("application/json")
                .content("{\"note\":\"越权确认\"}")).andExpect(status().isBadRequest());
        api.perform(get("/teaching/ocr/versions/v1/files/f1/pages/1/image")).andExpect(status().isBadRequest());
    }
}
