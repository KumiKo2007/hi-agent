package org.xhy.interfaces.api.portal.teaching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.teaching.service.TeachingSelectionAppService;
import org.xhy.infrastructure.repository.teaching.JdbcTeachingSelectionStore;
import org.xhy.infrastructure.auth.UserContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 公开 HTTP 到真实 PostgreSQL 的选材行为，不替换内部存储或选材规则。 */
@EnabledIfEnvironmentVariable(named = "TEACHING_INTEGRATION", matches = "true")
class TeachingSelectionHttpTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    JdbcTemplate jdbc;
    MockMvc api;
    private String schema;

    @BeforeEach
    void prepare() throws Exception {
        String url = System.getenv().getOrDefault("TEACHING_TEST_URL",
                "jdbc:postgresql://127.0.0.1:55425/teaching_test");
        schema = "teaching_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(url, "postgres", "teaching-test");
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        var ds = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                "postgres", "teaching-test");
        jdbc = new JdbcTemplate(ds);
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V20261020001__teaching_selections.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V20261020003__teaching_source_reviews.sql"));
        }
        jdbc.execute(
                "CREATE TABLE rag_versions (id text PRIMARY KEY, user_id text, original_rag_id text, version text, search_ready boolean, deleted_at timestamp)");
        jdbc.execute(
                "CREATE TABLE rag_version_files (id text PRIMARY KEY, rag_version_id text, file_name text, file_type text, file_page_size integer, deleted_at timestamp)");
        jdbc.execute(
                "CREATE TABLE rag_version_documents (id text PRIMARY KEY, rag_version_id text, rag_version_file_id text, page integer, content text, deleted_at timestamp, source_locator_type text, source_locator text, coverage_warnings text)");
        jdbc.update("INSERT INTO rag_versions VALUES ('v1','owner','dataset','1.0',true,null)");
        jdbc.update(
                "INSERT INTO rag_version_files VALUES ('f1','v1','导数.pdf','pdf',3,null),('f2','v1','未选资料.pdf','pdf',1,null)");
        jdbc.update(
                "INSERT INTO rag_version_documents(id,rag_version_id,rag_version_file_id,page,content,deleted_at) VALUES ('d1','v1','f1',0,'导数定义',null),('d2','v1','f1',1,'切线斜率',null),('d3','v1','f1',2,'导数应用',null),('d4','v1','f2',0,'不得混入',null)");
        api = controller();
        UserContext.setCurrentUserId("owner");
    }

    private MockMvc controller() {
        return MockMvcBuilders
                .standaloneSetup(new TeachingSelectionController(
                        new TeachingSelectionAppService(new JdbcTeachingSelectionStore(jdbc, json))))
                .setControllerAdvice(new org.xhy.infrastructure.config.GlobalExceptionHandler()).build();
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        if (jdbc != null)
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @Test
    void should_saveOnlySelectedPhysicalPagesAndReloadPinnedContent() throws Exception {
        String body = """
                {"title":"导数第一课","datasetId":"dataset","versionId":"v1",
                 "ranges":[{"fileId":"f1","startPage":2,"endPage":2,"chapter":"几何意义"}]}
                """;
        JsonNode selection = json
                .readTree(api.perform(post("/teaching/selections").contentType("application/json").content(body))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY")).andReturn()
                        .getResponse().getContentAsByteArray())
                .path("data");
        String id = selection.path("id").asText();
        assertEquals(1, selection.path("sources").size());
        assertEquals(2, selection.path("sources").get(0).path("physicalPage").asInt());
        assertEquals("切线斜率", selection.path("sources").get(0).path("content").asText());
        // 原来源后续变化不改已固定选材；重新创建控制器模拟刷新/重启。
        jdbc.update("UPDATE rag_version_documents SET content='后续修改' WHERE id='d2'");
        api = controller();
        api.perform(get("/teaching/selections/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value("v1"))
                .andExpect(jsonPath("$.data.sources[0].content").value("切线斜率"));
        api.perform(get("/teaching/selections/" + id + "/sources/d2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.physicalPage").value(2));
        api.perform(get("/teaching/selections/" + id + "/sources/d1")).andExpect(status().isBadRequest());
        UserContext.setCurrentUserId("other");
        api.perform(get("/teaching/selections/" + id)).andExpect(status().isBadRequest());
        api.perform(get("/teaching/selections/" + id + "/sources/d2")).andExpect(status().isBadRequest());
        api.perform(get("/teaching/selections")).andExpect(jsonPath("$.data.length()").value(0));
        api.perform(post("/teaching/selections").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }
    @Test
    void should_notMisrepresentNonPdfProcessingUnitsAsPhysicalPages() throws Exception {
        jdbc.update("UPDATE rag_version_files SET file_type='txt',file_name='知识梳理.txt' WHERE id='f1'");
        api.perform(post("/teaching/selections").contentType("application/json").content("""
                {"title":"文本资料","datasetId":"dataset","versionId":"v1",
                 "ranges":[{"fileId":"f1","startPage":1,"endPage":1,"chapter":"导数"}]}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.data.sources[0].locatorType").value("DOCUMENT_UNIT"));
    }

    @Test
    void should_preserveWordParagraphAndHeadingWithoutInventingPhysicalPages() throws Exception {
        jdbc.update("UPDATE rag_version_files SET file_type='docx',file_name='导数备课.docx' WHERE id='f1'");
        jdbc.update(
                "UPDATE rag_version_documents SET source_locator_type='WORD_PARAGRAPH',source_locator='{\"paragraphIndex\":2,\"heading\":\"导数的概念\"}',coverage_warnings='[]' WHERE id='d2'");
        JsonNode selected = json
                .readTree(api.perform(post("/teaching/selections").contentType("application/json").content("""
                        {"title":"Word段落","datasetId":"dataset","versionId":"v1",
                         "ranges":[{"fileId":"f1","startPage":2,"endPage":2,"chapter":"导数"}]}
                        """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"))
                        .andExpect(jsonPath("$.data.sources[0].physicalPage").isEmpty())
                        .andExpect(jsonPath("$.data.sources[0].paragraphIndex").value(2))
                        .andExpect(jsonPath("$.data.sources[0].heading").value("导数的概念")).andReturn().getResponse()
                        .getContentAsByteArray())
                .path("data");
        String id = selected.path("id").asText();
        jdbc.update("UPDATE rag_version_documents SET source_locator='{}',content='已经修改' WHERE id='d2'");
        api.perform(get("/teaching/selections/" + id + "/sources/d2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paragraphIndex").value(2))
                .andExpect(jsonPath("$.data.heading").value("导数的概念"))
                .andExpect(jsonPath("$.data.content").value("切线斜率"));
        UserContext.setCurrentUserId("other");
        api.perform(get("/teaching/selections/" + id + "/sources/d2")).andExpect(status().isBadRequest());
    }

    @Test
    void should_discloseMissingPagesAndRejectForeignFilesOrUnreadyVersions() throws Exception {
        jdbc.update("DELETE FROM rag_version_documents WHERE id='d2'");
        String request = """
                {"title":"导数模块","datasetId":"dataset","versionId":"v1",
                 "ranges":[{"fileId":"f1","startPage":1,"endPage":3,"chapter":"导数"}]}
                """;
        api.perform(post("/teaching/selections").contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.data.sources.length()").value(2))
                .andExpect(jsonPath("$.data.warnings[0]").value(org.hamcrest.Matchers.containsString("物理第 2 页缺少可用来源")));
        api.perform(post("/teaching/selections").contentType("application/json")
                .content(request.replace("f1", "foreign-file"))).andExpect(status().isBadRequest());
        api.perform(post("/teaching/selections").contentType("application/json")
                .content(request.replace("\"endPage\":3", "\"endPage\":999"))).andExpect(status().isBadRequest());
        api.perform(post("/teaching/selections").contentType("application/json")
                .content(request.replace("\"startPage\":1", "\"startPage\":0"))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE rag_version_files SET file_page_size=null WHERE id='f1'");
        api.perform(post("/teaching/selections").contentType("application/json")
                .content(request.replace("\"startPage\":1", "\"startPage\":2147483647").replace("\"endPage\":3",
                        "\"endPage\":2147483647")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.data.warnings[0]").value(org.hamcrest.Matchers.containsString("2147483647")));
        jdbc.update("UPDATE rag_versions SET search_ready=false WHERE id='v1'");
        api.perform(post("/teaching/selections").contentType("application/json").content(request))
                .andExpect(status().isBadRequest());
    }

}
