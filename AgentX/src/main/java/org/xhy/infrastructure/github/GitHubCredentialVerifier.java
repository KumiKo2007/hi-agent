package org.xhy.infrastructure.github;

import java.util.List;

/** 仅服务端使用的凭据核验边界。 */
public interface GitHubCredentialVerifier {
    Identity verify(String token, List<String> repositories);

    record Identity(String id, String login) {
    }
}
