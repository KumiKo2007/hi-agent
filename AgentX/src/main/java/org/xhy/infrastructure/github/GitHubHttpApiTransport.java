package org.xhy.infrastructure.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.xhy.infrastructure.exception.BusinessException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 凭据只随发往固定 GitHub API 主机的服务端请求发送。 */
@Component
public class GitHubHttpApiTransport implements GitHubApiTransport {
    private static final URI API_ROOT = URI.create("https://api.github.com");
    private final HttpClient client;
    private final ObjectMapper mapper;

    public GitHubHttpApiTransport(ObjectMapper mapper) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = mapper;
    }

    @Override
    public JsonNode get(String token, String path) {
        if (!path.equals("/user") && !path.matches("/user/repos\\?per_page=100&page=[0-9]{1,2}")) {
            throw new BusinessException("不允许的 GitHub API 读取路径");
        }
        HttpRequest request = HttpRequest.newBuilder(API_ROOT.resolve(path)).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "hi-agent").header("Authorization", "Bearer " + token).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException("GitHub 凭据无效或无法读取所选仓库");
            }
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new BusinessException("无法连接 GitHub，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("GitHub 连接核验中断");
        }
    }
}
