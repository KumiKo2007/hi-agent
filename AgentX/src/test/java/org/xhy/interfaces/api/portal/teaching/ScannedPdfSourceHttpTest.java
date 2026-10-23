package org.xhy.interfaces.api.portal.teaching;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Base64;
import javax.imageio.ImageIO;
import com.sun.net.httpserver.HttpServer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.dromara.x.file.storage.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.xhy.domain.rag.message.RagDocMessage;
import org.xhy.domain.rag.model.ModelConfig;
import org.xhy.domain.rag.repository.*;
import org.xhy.domain.rag.strategy.impl.PDFRagDocDocumentProcessing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** 实际图像PDF→真实文件存储/PG→外部OCR HTTP替身→逐页公开HTTP，替身只在外部模型边界。 */
@EnabledIfEnvironmentVariable(named = "TEACHING_INTEGRATION", matches = "true")
class ScannedPdfSourceHttpTest extends TeachingOcrHttpTest {
    FileStorageService storage;
    Path directory;
    PDFRagDocDocumentProcessing processor;
    HttpServer external;
    String originalFile;
    SqlSessionTemplate session;

    @BeforeEach
    void storageAndExternalOcr() throws Exception {
        directory = Files.createTempDirectory("hi-agent-scanned-pdf-");
        String init = Files.readString(Path.of("../docs/sql/01_init.sql"));
        for (String table : List.of("document_unit", "file_detail")) {
            var matcher = java.util.regex.Pattern
                    .compile("create table public\\." + table + " \\(.*?\\n\\);", java.util.regex.Pattern.DOTALL)
                    .matcher(init);
            assertTrue(matcher.find());
            jdbc.execute(matcher.group().replace("public.", ""));
        }
        try (var connection = jdbc.getDataSource().getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("db/migration/V20261020002__word_source_locations.sql"));
        }
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(jdbc.getDataSource());
        var global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        var db = new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig();
        db.setLogicDeleteField("deletedAt");
        db.setLogicDeleteValue("now()");
        db.setLogicNotDeleteValue("null");
        global.setDbConfig(db);
        global.setMetaObjectHandler(new org.xhy.infrastructure.config.MybatisPlusConfig());
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(configuration, global);
        configuration.addMapper(DocumentUnitRepository.class);
        configuration.addMapper(FileDetailRepository.class);
        configuration.addMapper(RagVersionRepository.class);
        configuration.addMapper(RagVersionFileRepository.class);
        configuration.addMapper(RagVersionDocumentRepository.class);
        configuration.addMapper(RagQaDatasetRepository.class);
        factory.setGlobalConfig(global);
        factory.setConfiguration(configuration);
        session = new SqlSessionTemplate(factory.getObject());
        var properties = new FileStorageProperties();
        var local = new FileStorageProperties.LocalPlusConfig();
        local.setPlatform("ocr-fixture");
        local.setStoragePath(directory + "/");
        local.setBasePath("");
        local.setDomain("http://ocr-fixture.local/");
        properties.setDefaultPlatform("ocr-fixture");
        properties.setLocalPlus(List.of(local));
        var strategy = new org.xhy.application.file.strategy.RagFileStorageStrategy(
                session.getMapper(FileDetailRepository.class), session.getMapper(DocumentUnitRepository.class),
                new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>());
        var recorder = new org.xhy.application.file.service.FileStorageAppService(
                new org.xhy.application.file.factory.FileStorageStrategyFactory(
                        java.util.Map.of(org.xhy.domain.file.constant.FileTypeEnum.RAG, strategy)));
        storage = FileStorageServiceBuilder.create(properties).useDefault().setFileRecorder(recorder).build();
        processor = new PDFRagDocDocumentProcessing(session.getMapper(DocumentUnitRepository.class),
                session.getMapper(FileDetailRepository.class));
        org.springframework.test.util.ReflectionTestUtils.setField(processor, "fileStorageService", storage);
        external = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        external.createContext("/", exchange -> {
            var request = json.readTree(exchange.getRequestBody().readAllBytes());
            String image = request.path("messages").get(0).path("content").get(0).path("image_url").path("url")
                    .asText();
            var raster = ImageIO.read(
                    new ByteArrayInputStream(Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1))));
            Color color = new Color(raster.getRGB(4, 4));
            boolean failed = color.getGreen() > 200 && color.getRed() < 100;
            String text = color.getBlue() > 200
                    ? ""
                    : color.getRed() > 200 && color.getGreen() > 200 ? "公式残缺 \\(x^{2\\)" : "导数是变化率。$f(x)=x^2$，$f'(1)=2$。";
            byte[] response = failed
                    ? "{\"error\":{\"message\":\"fixture page unavailable\",\"type\":\"invalid_request_error\"}}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : json.writeValueAsBytes(java.util.Map.of("id", "ocr-fixture", "object", "chat.completion",
                            "created", 1, "model", "fixture", "choices", List.of(java.util.Map.of("index", 0, "message",
                                    java.util.Map.of("role", "assistant", "content", text), "finish_reason", "stop"))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(failed ? 400 : 200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        external.start();
        api = controller(storage);
    }
    @AfterEach
    void cleanupExternal() throws Exception {
        if (external != null)
            external.stop(0);
        if (storage != null)
            storage.destroy();
        if (directory != null)
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
    }
    private byte[] scannedPdf() throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            int i = 0;
            for (Color color : List.of(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)) {
                var raster = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
                var g = raster.createGraphics();
                g.setColor(color);
                g.fillRect(0, 0, 640, 360);
                g.setColor(Color.BLACK);
                g.setFont(new Font("Serif", Font.PLAIN, 28));
                g.drawString("Derivative page " + (++i), 40, 100);
                g.drawString("f(x) = x^2; f'(1) = 2", 40, 180);
                g.dispose();
                var page = new PDPage(new PDRectangle(320, 180));
                doc.addPage(page);
                try (var content = new PDPageContentStream(doc, page)) {
                    content.drawImage(LosslessFactory.createFromImage(doc, raster), 0, 0, 320, 180);
                }
                raster.flush();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
    @Test
    void should_showOcrFailureEmptyResultAndFormulaGapWithoutDroppingPhysicalPages() throws Exception {
        byte[] fixture = scannedPdf();
        try (var document = org.apache.pdfbox.Loader.loadPDF(fixture)) {
            assertEquals(4, document.getNumberOfPages());
            assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(document).isBlank(), "fixture必须为真实扫描图像PDF");
        }
        var uploaded = storage.of(fixture).setOriginalFilename("自编导数扫描样例.pdf")
                .setMetadata(java.util.Map.of("dataset", "dataset", "userid", "owner"))
                .setNotSupportMetadataThrowException(false).upload();
        assertNotNull(uploaded);
        originalFile = uploaded.getId();
        var message = new RagDocMessage();
        message.setFileId(originalFile);
        message.setUserId("owner");
        message.setOcrModelConfig(
                new ModelConfig("fixture-only", "http://127.0.0.1:" + external.getAddress().getPort() + "/v1",
                        org.xhy.domain.llm.model.enums.ModelType.CHAT,
                        org.xhy.infrastructure.llm.protocol.enums.ProviderProtocol.OPENAI, "fixture"));
        processor.handle(message, "PDF");
        api.perform(get("/teaching/ocr/files/" + originalFile)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pages.length()").value(4))
                .andExpect(jsonPath("$.data.pages[0].coverageWarnings[0]")
                        .value(org.hamcrest.Matchers.containsString("教师")))
                .andExpect(jsonPath("$.data.pages[1].status").value("MISSING"))
                .andExpect(jsonPath("$.data.pages[2].status").value("MISSING"))
                .andExpect(jsonPath("$.data.pages[3].status").value("FORMULA_GAP"));
        String image = json
                .readTree(api.perform(get("/teaching/ocr/files/" + originalFile + "/pages/4/image"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
                .path("data").asText();
        var rendered = ImageIO
                .read(new ByteArrayInputStream(Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1))));
        assertTrue(rendered.getWidth() > 300);
        assertTrue(new Color(rendered.getRGB(4, 4)).getGreen() > 200, "原页必须是第4页黄色样例，不能串页");
        // 快照必须使用部署表的NOT NULL约束及生产ready-version HTTP，不能用手工INSERT绕过复制链路。
        String init = Files.readString(Path.of("../docs/sql/01_init.sql"));
        for (String table : List.of("rag_versions", "rag_version_files", "rag_version_documents", "ai_rag_qa_dataset")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
            var matcher = java.util.regex.Pattern
                    .compile("create table public\\." + table + " \\(.*?\\n\\);", java.util.regex.Pattern.DOTALL)
                    .matcher(init);
            assertTrue(matcher.find(), "必须复用真实部署schema");
            jdbc.execute(matcher.group().replace("public.", ""));
        }
        try (var connection = jdbc.getDataSource().getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("db/migration/V20261020002__word_source_locations.sql"));
        }
        jdbc.update("INSERT INTO ai_rag_qa_dataset(id,name,user_id) VALUES('dataset','扫描教材','owner')");
        // 本slice边界起点为已完成向量化文件；上传/MQ/JWT完整路径另由真实浏览器验收覆盖。
        jdbc.update("UPDATE file_detail SET processing_status=4 WHERE id=?", originalFile);
        var datasetService = new org.xhy.domain.rag.service.RagQaDatasetDomainService(session.getMapper(RagQaDatasetRepository.class));
        var versionService = new org.xhy.domain.rag.service.management.RagVersionDomainService(
                session.getMapper(RagVersionRepository.class), session.getMapper(RagVersionFileRepository.class),
                session.getMapper(RagVersionDocumentRepository.class), datasetService,
                session.getMapper(FileDetailRepository.class), session.getMapper(DocumentUnitRepository.class));
        var files = new org.xhy.domain.rag.service.FileDetailDomainService(storage, session.getMapper(FileDetailRepository.class),
                new org.xhy.domain.rag.service.FileProcessingStateMachineService(List.of()));
        var app = new org.xhy.application.rag.service.manager.RagQaDatasetAppService(datasetService, files,
                null, null, null, null, versionService, null, null, null,
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        var readyApi = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new org.xhy.interfaces.api.portal.rag.RagQaDatasetController(app))
                .setControllerAdvice(new org.xhy.infrastructure.config.GlobalExceptionHandler()).build();
        String versionId = json.readTree(readyApi.perform(post("/rag/datasets/dataset/ready-versions"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data").path("id").asText();
        var preview = json.readTree(readyApi.perform(get("/rag/datasets/dataset/ready-versions/" + versionId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data");
        String fileId = preview.path("files").get(0).path("id").asText();
        String fixed = "/teaching/ocr/versions/" + versionId + "/files/" + fileId;
        api.perform(get(fixed)).andExpect(jsonPath("$.data.pages.length()").value(4))
                .andExpect(jsonPath("$.data.pages[1].status").value("MISSING"))
                .andExpect(jsonPath("$.data.pages[1].coverageWarnings[0]").value(org.hamcrest.Matchers.containsString("失败")))
                .andExpect(jsonPath("$.data.pages[3].status").value("FORMULA_GAP"));
        api.perform(post(fixed + "/pages/1/reviews").contentType("application/json")
                .content("{\"note\":\"已对照第一页定义和公式；仍保留自动警告\"}")).andExpect(status().isOk());
        String request = json.writeValueAsString(java.util.Map.of("title", "仅保留完整首页", "datasetId", "dataset", "versionId", versionId,
                "ranges", List.of(java.util.Map.of("fileId", fileId, "startPage", 1, "endPage", 1))));
        api.perform(post("/teaching/selections").contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.sources.length()").value(1))
                .andExpect(jsonPath("$.data.sources[0].physicalPage").value(1))
                .andExpect(jsonPath("$.data.sources[0].review.note").value("已对照第一页定义和公式；仍保留自动警告"));
    }
}
