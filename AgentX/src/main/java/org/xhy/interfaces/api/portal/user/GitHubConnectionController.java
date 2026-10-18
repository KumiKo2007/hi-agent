package org.xhy.interfaces.api.portal.user;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xhy.application.github.dto.GitHubConnectionDTO;
import org.xhy.application.github.service.GitHubConnectionAppService;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.interfaces.api.common.Result;
import org.xhy.interfaces.dto.github.request.ConnectGitHubRequest;
import org.xhy.interfaces.dto.github.request.NarrowGitHubRepositoriesRequest;

/** 当前使用者的 GitHub 连接。任何响应都不包含凭据。 */
@RestController
@RequestMapping("/users/github-connection")
public class GitHubConnectionController {
    private final GitHubConnectionAppService service;

    public GitHubConnectionController(GitHubConnectionAppService service) {
        this.service = service;
    }

    @GetMapping
    public Result<GitHubConnectionDTO> get() {
        return Result.success(service.get(UserContext.getCurrentUserId()));
    }

    @PostMapping
    public Result<GitHubConnectionDTO> connect(@RequestBody @Validated ConnectGitHubRequest request) {
        return Result.success(service.connect(UserContext.getCurrentUserId(), request.token(), request.repositories()));
    }

    @PutMapping("/repositories")
    public Result<GitHubConnectionDTO> narrowRepositories(
            @RequestBody @Validated NarrowGitHubRepositoriesRequest request) {
        return Result.success(service.narrowRepositories(UserContext.getCurrentUserId(), request.repositories()));
    }

    @DeleteMapping
    public Result<Void> revoke() {
        service.revoke(UserContext.getCurrentUserId());
        return Result.success();
    }
}
