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

/** 大纲实际HTTP与PG及模型权限链；仅替换外部生成模型响应，在UserContext注入测试身份，未验证JWT。 */
public class TeachingOutlineBrowserHarness {
    public static void main(String[] args) throws Exception {
        var fixture = new TeachingOutlineHttpTest();
        fixture.prepare();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8089), 0);
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"userId\":\"owner\",\"exp\":2540188800}".getBytes(StandardCharsets.UTF_8));
        String ownerToken = "e30." + payload + ".owner-test";
        String otherToken = "e30." + payload + ".other-test";
        server.createContext("/api", exchange -> {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization,Content-Type");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS");
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
        System.out.println("OUTLINE_HTTP_HARNESS=" + new ObjectMapper().writeValueAsString(
                Map.of("ownerToken", ownerToken, "otherToken", otherToken, "selectionId", "selected")));
        new CountDownLatch(1).await();
    }
}
