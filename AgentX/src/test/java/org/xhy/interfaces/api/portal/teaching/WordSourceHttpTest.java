package org.xhy.interfaces.api.portal.teaching;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.xhy.domain.rag.message.RagDocMessage;
import org.xhy.domain.rag.repository.DocumentUnitRepository;
import org.xhy.domain.rag.repository.FileDetailRepository;
import org.xhy.domain.rag.strategy.impl.WORDDocumentProcessing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 DOCX、文件存储及PG导入的结果通过公开选材HTTP观察；不冒称验证上传/MQ/JWT全链路。 */
@EnabledIfEnvironmentVariable(named = "TEACHING_INTEGRATION", matches = "true")
class WordSourceHttpTest extends TeachingSelectionHttpTest {
    private FileStorageService storage;
    private Path directory;
    private WORDDocumentProcessing processor;
    private org.xhy.domain.rag.service.FileDetailDomainService fileService;

    @BeforeEach
    void wordStorage() throws Exception {
        directory = Files.createTempDirectory("hi-agent-word-");
        String init = Files.readString(Path.of("../docs/sql/01_init.sql"));
        for (String table : List.of("document_unit", "file_detail")) {
            var matcher = java.util.regex.Pattern
                    .compile("create table public\\." + table + " \\(.*?\\n\\);", java.util.regex.Pattern.DOTALL)
                    .matcher(init);
            assertTrue(matcher.find(), "全新安装必须包含真实导入表");
            jdbc.execute(matcher.group().replace("public.", ""));
        }
        try (var connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V20261020002__word_source_locations.sql"));
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(jdbc.getDataSource());
        var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        var db = new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig();
        db.setLogicDeleteField("deletedAt");
        db.setLogicDeleteValue("now()");
        db.setLogicNotDeleteValue("null");
        global.setDbConfig(db);
        global.setMetaObjectHandler(new org.xhy.infrastructure.config.MybatisPlusConfig());
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config, global);
        config.addMapper(DocumentUnitRepository.class);
        config.addMapper(FileDetailRepository.class);
        factory.setGlobalConfig(global);
        factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        var properties = new FileStorageProperties();
        var local = new FileStorageProperties.LocalPlusConfig();
        local.setPlatform("word-fixture");
        local.setStoragePath(directory.toString() + "/");
        local.setBasePath("");
        local.setDomain("http://word-fixture.local/");
        properties.setDefaultPlatform("word-fixture");
        properties.setLocalPlus(List.of(local));
        var ragStorage = new org.xhy.application.file.strategy.RagFileStorageStrategy(
                session.getMapper(FileDetailRepository.class), session.getMapper(DocumentUnitRepository.class),
                new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>());
        var recorder = new org.xhy.application.file.service.FileStorageAppService(
                new org.xhy.application.file.factory.FileStorageStrategyFactory(
                        java.util.Map.of(org.xhy.domain.file.constant.FileTypeEnum.RAG, ragStorage)));
        storage = FileStorageServiceBuilder.create(properties).useDefault()
                .addFileWrapperAdapter(new org.dromara.x.file.storage.spring.file.MultipartFileWrapperAdapter())
                .setFileRecorder(recorder).build();
        fileService = new org.xhy.domain.rag.service.FileDetailDomainService(storage,
                session.getMapper(FileDetailRepository.class),
                new org.xhy.domain.rag.service.FileProcessingStateMachineService(
                        List.of(new org.xhy.domain.rag.service.state.impl.UploadedStateProcessor())));
        processor = new WORDDocumentProcessing(session.getMapper(DocumentUnitRepository.class),
                session.getMapper(FileDetailRepository.class), storage);
    }

    @AfterEach
    void removeWordStorage() throws Exception {
        if (storage != null)
            storage.destroy();
        if (directory != null) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }

    private void ingest(boolean complex) throws Exception {
        byte[] bytes;
        try (var document = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            document.createParagraph().setStyle("Heading1");
            document.getParagraphs().get(0).createRun().setText("导数的概念");
            document.createParagraph(); // 原文空段必须计数，后面的第3段不能被移位。
            document.createParagraph().createRun().setText("割线的斜率趋近于切线的斜率。".repeat(60));
            document.createParagraph().createRun().setText("本段不得纳入选材。");
            if (complex) {
                document.createTable(1, 1).getRow(0).getCell(0).setText("表格资料");
                document.getParagraphs().get(2).getCTP().addNewOMath().addNewR().addNewT().setStringValue("x²");
            }
            document.write(out);
            bytes = out.toByteArray();
        }
        var upload = new org.xhy.domain.rag.model.FileDetailEntity();
        upload.setDataSetId("dataset");
        upload.setUserId("owner");
        upload.setMultipartFile(new org.springframework.mock.web.MockMultipartFile("file", "导数备课.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes));
        var uploaded = fileService.uploadFileToDataset(upload);
        assertNotNull(uploaded);
        var message = new RagDocMessage();
        message.setFileId(uploaded.getId());
        processor.handle(message, "DOCX");
        // 固定版本fixture复用生产元数据列，HTTP断言不查询数据库观察结果。
        jdbc.update(
                "UPDATE rag_version_files SET file_type='docx',file_name='导数备课.docx',file_page_size=4 WHERE id='f1'");
        jdbc.update("DELETE FROM rag_version_documents WHERE rag_version_file_id='f1'");
        jdbc.update(
                "INSERT INTO rag_version_documents(id,rag_version_id,rag_version_file_id,page,content,source_locator_type,source_locator,coverage_warnings) SELECT id,'v1','f1',page,content,source_locator_type,source_locator,coverage_warnings FROM document_unit WHERE file_id=?",
                uploaded.getId());
    }

    @Test
    void should_keepOneLongOriginalParagraphAndItsHeadingThroughSourceHttp() throws Exception {
        ingest(false);
        var response = api
                .perform(post("/teaching/selections").contentType("application/json").content(
                        """
                                {"title":"原文第三段","datasetId":"dataset","versionId":"v1","ranges":[{"fileId":"f1","startPage":3,"endPage":3,"chapter":"导数"}]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.sources.length()").value(1))
                .andExpect(jsonPath("$.data.sources[0].paragraphIndex").value(3))
                .andExpect(jsonPath("$.data.sources[0].heading").value("导数的概念"))
                .andExpect(jsonPath("$.data.sources[0].physicalPage").isEmpty())
                .andExpect(jsonPath("$.data.sources[0].content").value("割线的斜率趋近于切线的斜率。".repeat(60))).andReturn()
                .getResponse().getContentAsByteArray();
        var selection = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data");
        api.perform(get(selection.path("sources").get(0).path("sourceUrl").asText())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paragraphIndex").value(3));
    }

    @Test
    void should_keepEmptyOriginalParagraphInRangeWithoutCallingItMissingContent() throws Exception {
        ingest(false);
        api.perform(post("/teaching/selections").contentType("application/json").content(
                """
                        {"title":"含原文空段","datasetId":"dataset","versionId":"v1","ranges":[{"fileId":"f1","startPage":1,"endPage":3,"chapter":"导数"}]}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.sources.length()").value(3))
                .andExpect(jsonPath("$.data.sources[1].paragraphIndex").value(2))
                .andExpect(jsonPath("$.data.sources[1].content").value(""));
    }

    @Test
    void should_discloseUncoveredEquationAndTableInSavedSource() throws Exception {
        ingest(true);
        api.perform(post("/teaching/selections").contentType("application/json").content(
                """
                        {"title":"含公式资料","datasetId":"dataset","versionId":"v1","ranges":[{"fileId":"f1","startPage":3,"endPage":3,"chapter":"导数"}]}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.data.sources[0].coverageWarnings")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("公式"))))
                .andExpect(jsonPath("$.data.sources[0].coverageWarnings")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("表格"))));
    }
}
