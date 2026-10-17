package org.xhy.interfaces.dto.github.request;

import java.util.List;

public record ConnectGitHubRequest(String token, List<String> repositories) {
}
