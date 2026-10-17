package org.xhy.infrastructure.github;

import com.fasterxml.jackson.databind.JsonNode;

/** 只读 GitHub API 请求。路径只由受信任的服务端核验器提供。 */
public interface GitHubApiTransport {
    JsonNode get(String token, String path);
}
