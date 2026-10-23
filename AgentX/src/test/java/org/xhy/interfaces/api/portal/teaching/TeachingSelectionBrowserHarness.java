package org.xhy.interfaces.api.portal.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.xhy.infrastructure.auth.UserContext;

/** 仅将实际选材 controller/app/store 暴露到本机；不替换知识库、上传、身份认证等生产实现。 浏览器 slice 只验证选材 HTTP。测试身份在 UserContext seam 注入，不声称验证 JWT。 */
public class TeachingSelectionBrowserHarness {
    public static void main(String[] args) throws Exception {
        var fixture = new TeachingSelectionHttpTest();
        fixture.prepare();
        fixture.jdbc.update(
                "INSERT INTO rag_version_files(id,rag_version_id,file_name,file_type,file_page_size) VALUES('word-f1','v1','导数备课.docx','docx',3)");
        fixture.jdbc.update(
                "INSERT INTO rag_version_documents(id,rag_version_id,rag_version_file_id,page,content,source_locator_type,source_locator,coverage_warnings) VALUES('word-d3','v1','word-f1',2,'割线的斜率趋近于切线的斜率。','WORD_PARAGRAPH','{\"paragraphIndex\":3,\"heading\":\"导数的概念\"}','[\"Word 含公式，公式结构未覆盖，请核查\"]')");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8088), 0);
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"userId\":\"owner\",\"exp\":2540188800}".getBytes(StandardCharsets.UTF_8));
        String ownerToken = "e30." + payload + ".owner-test";
        String otherToken = "e30." + payload + ".other-test";
        server.createContext("/api", exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization,Content-Type");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (!("Bearer " + ownerToken).equals(auth) && !("Bearer " + otherToken).equals(auth)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            UserContext.setCurrentUserId(("Bearer " + ownerToken).equals(auth) ? "owner" : "other");
            try {
                String path = exchange.getRequestURI().toString().substring(4);
                var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(exchange.getRequestMethod()),
                        URI.create(path));
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType != null)
                    request.contentType(contentType);
                request.content(exchange.getRequestBody().readAllBytes());
                var response = fixture.api.perform(request).andReturn().getResponse();
                exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                byte[] bytes = response.getContentAsByteArray();
                exchange.sendResponseHeaders(response.getStatus(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                UserContext.clear();
                exchange.close();
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            fixture.cleanup();
        }));
        server.start();
        System.out.println("TEACHING_HTTP_HARNESS=" + new ObjectMapper().writeValueAsString(Map.of("ownerToken",
                ownerToken, "otherToken", otherToken, "datasetId", "dataset", "versionId", "v1", "fileId", "f1")));
        new CountDownLatch(1).await();
    }
}
