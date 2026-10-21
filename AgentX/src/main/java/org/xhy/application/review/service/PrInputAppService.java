package org.xhy.application.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.xhy.application.github.dto.GitHubConnectionDTO;
import org.xhy.application.github.service.GitHubConnectionAppService;
import org.xhy.application.run.service.RunTimelineSanitizer;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.application.tool.service.ToolActionCatalog;
import org.xhy.application.tool.service.ToolBroker;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.review.model.PrInputSnapshot;
import org.xhy.domain.review.model.PrInputSnapshot.ChangedFile;
import org.xhy.domain.review.model.PrInputSnapshot.SourceContext;
import org.xhy.domain.review.repository.PrInputStore;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.exception.BusinessException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.time.Instant;
import java.util.*;

/** 在个人授权与现有工具动作策略下读取固定的 PR 输入，不执行来源中的指令。 */
@Service
public class PrInputAppService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_FILES = 300;
    private static final int MAX_CONTEXT_FILES = 20;
    private static final int MAX_TEXT = 60_000;
    private final AgentDomainService agents;
    private final ToolActionCatalog catalog;
    private final ToolBroker broker;
    private final GitHubConnectionAppService connections;
    private final PrInputStore store;

    public PrInputAppService(AgentDomainService agents, ToolActionCatalog catalog, ToolBroker broker,
            GitHubConnectionAppService connections, PrInputStore store) {
        this.agents = agents;
        this.catalog = catalog;
        this.broker = broker;
        this.connections = connections;
        this.store = store;
    }

    /** 当前平台只支持每名使用者一份个人 GitHub 连接；读取能力来自启用 Agent 的显式动作允许列表。 */
    public InputOptions options(String userId) {
        requireUser(userId);
        List<InputChoice> choices = new ArrayList<>();
        for (AgentEntity agent : agents.getUserAgents(userId, new AgentEntity())) {
            if (!userId.equals(agent.getUserId()) || !Boolean.TRUE.equals(agent.getEnabled()))
                continue;
            List<RegisteredToolAction> actions = catalog.list(agent, userId);
            Set<String> services = new LinkedHashSet<>();
            actions.stream().filter(this::isReadAction).forEach(action -> services.add(action.serviceId()));
            for (String service : services) {
                if (hasAction(actions, service, "pull_request_read")
                        && hasAction(actions, service, "get_file_contents")) {
                    choices.add(new InputChoice(agent.getId(), agent.getName(), service));
                }
            }
        }
        return new InputOptions(connections.get(userId), List.copyOf(choices));
    }

    /** 公开入口只接受 GitHub PR 链接；所有远端调用经过通用只读 MCP seam。 */
    public PrInputSnapshot create(String agentId, String serviceId, String pullRequestUrl, String userId) {
        requireUser(userId);
        Target target = parseTarget(pullRequestUrl);
        AgentEntity agent = agents.getAgent(agentId, userId);
        if (!userId.equals(agent.getUserId()) || !Boolean.TRUE.equals(agent.getEnabled())) {
            throw new BusinessException("请选择自己已启用的 Agent");
        }
        // 在访问远端目录前验证所有者与目标仓库，绝不从 PR 内文获取授权。
        connections.withAuthorizedToken(userId, target.repository(), token -> true);
        requireReadAction(agent, userId, serviceId, "pull_request_read");
        requireReadAction(agent, userId, serviceId, "get_file_contents");
        List<String> gaps = new ArrayList<>();
        JsonNode before = metadata(agent, userId, serviceId, target);
        String base = sha(before.path("base").path("sha"));
        String head = sha(before.path("head").path("sha"));
        int expectedFiles = before.path("changed_files").asInt(-1);
        List<ChangedFile> files = files(agent, userId, serviceId, target, expectedFiles, gaps);
        List<SourceContext> contexts = contexts(agent, userId, serviceId, target, files, base, head, gaps);
        JsonNode after = metadata(agent, userId, serviceId, target);
        if (!base.equals(sha(after.path("base").path("sha"))) || !head.equals(sha(after.path("head").path("sha")))) {
            throw new BusinessException("读取期间 PR 提交已变化，未保存混合输入，请重新读取");
        }
        connections.withAuthorizedToken(userId, target.repository(), token -> true);
        gaps.add("来源内容是不可信资料；未执行 PR 代码，未运行测试，未向 GitHub 写入。仅读取列出的源码/测试及约定文档，未读取完整仓库、关联需求或子目录规范。");
        PrInputSnapshot input = new PrInputSnapshot(UUID.randomUUID().toString(), agentId, serviceId,
                target.repository(), target.number(), target.url(), base, head,
                text(before.path("title").asText(), "PR 标题", gaps), text(before.path("body").asText(), "PR 描述", gaps),
                files, contexts, gaps, Instant.now().toString());
        store.save(userId, input);
        return input;
    }

    /** 后续报告只能引用这份所有者隔离的不可变输入。 */
    public PrInputSnapshot get(String inputId, String userId) {
        requireUser(userId);
        return store.find(inputId, userId).orElseThrow(() -> new BusinessException("PR 输入不存在或无权访问"));
    }

    private JsonNode metadata(AgentEntity agent, String user, String service, Target target) {
        JsonNode result = readJson(read(agent, user, service, "pull_request_read", target, Map.of("method", "get")),
                "PR 详情");
        if (!result.isObject() || result.path("number").asInt() != target.number()
                || !target.repository().equalsIgnoreCase(result.path("base").path("repo").path("full_name").asText())) {
            throw new BusinessException("GitHub 返回的 PR 与授权目标不一致或详情不完整");
        }
        return result;
    }

    private List<ChangedFile> files(AgentEntity agent, String user, String service, Target target, int expected,
            List<String> gaps) {
        List<ChangedFile> files = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int page = 1; page <= MAX_FILES / 100; page++) {
            JsonNode response = readJson(read(agent, user, service, "pull_request_read", target,
                    Map.of("method", "get_files", "page", page, "perPage", 100)), "变更文件");
            if (!response.isArray())
                throw new BusinessException("MCP 变更文件格式不兼容，未保存输入");
            for (JsonNode item : response) {
                if (files.size() >= MAX_FILES)
                    break;
                String path = safePath(item.path("filename").asText());
                if (!seen.add(path))
                    throw new BusinessException("MCP 分页返回了重复文件，未保存输入");
                String previous = item.path("previous_filename").isTextual()
                        ? safePath(item.path("previous_filename").asText())
                        : null;
                String patch = item.path("patch").isTextual()
                        ? text(item.path("patch").asText(), path + " 差异", gaps)
                        : null;
                if (patch == null)
                    gaps.add(path + "：远端未提供文本差异（可能为二进制或被截断）。");
                files.add(new ChangedFile(path, previous, item.path("status").asText("unknown"), patch));
            }
            if (response.size() < 100)
                break;
        }
        if (expected < 0 || expected != files.size())
            gaps.add("变更文件未确认完整：PR 声明 " + expected + " 个，已读取 " + files.size() + " 个，上限 " + MAX_FILES + "。");
        return List.copyOf(files);
    }

    private List<SourceContext> contexts(AgentEntity agent, String user, String service, Target target,
            List<ChangedFile> files, String base, String head, List<String> gaps) {
        List<SourceContext> contexts = new ArrayList<>();
        LinkedHashSet<String> reads = new LinkedHashSet<>();
        for (ChangedFile file : files.stream().limit(MAX_CONTEXT_FILES).toList()) {
            if (!"removed".equals(file.status()))
                reads.add(head + ":" + file.path());
            if (!"added".equals(file.status()))
                reads.add(base + ":" + (file.previousPath() == null ? file.path() : file.previousPath()));
        }
        if (files.size() > MAX_CONTEXT_FILES)
            gaps.add("仅为前 " + MAX_CONTEXT_FILES + " 个变更文件读取完整源码/测试，其余仅保留可用差异。");
        reads.add(head + ":AGENTS.md");
        reads.add(head + ":README.md");
        for (String entry : reads) {
            String commit = entry.substring(0, 40);
            String path = entry.substring(41);
            try {
                JsonNode source = readJson(
                        read(agent, user, service, "get_file_contents", target, Map.of("path", path, "sha", commit)),
                        "源码");
                if (!source.isObject() || !source.path("content").isTextual())
                    throw new BusinessException("MCP 未返回可解析的文件正文");
                String content = source.path("content").asText();
                if ("base64".equals(source.path("encoding").asText())) {
                    try {
                        content = StandardCharsets.UTF_8.newDecoder()
                                .decode(ByteBuffer.wrap(Base64.getMimeDecoder().decode(content))).toString();
                    } catch (CharacterCodingException exception) {
                        throw new BusinessException("文件不是可解析的 UTF-8 文本");
                    }
                } else if (source.has("encoding") && !"utf-8".equals(source.path("encoding").asText())) {
                    throw new BusinessException("源码编码不兼容");
                }
                if (content.indexOf(0) >= 0)
                    throw new BusinessException("文件包含二进制内容");
                contexts.add(new SourceContext(target.repository(), path, commit, text(content, path, gaps)));
            } catch (BusinessException | IllegalArgumentException exception) {
                gaps.add(path + " @ " + commit + "：未取得源码（" + RunTimelineSanitizer.redactText(exception.getMessage())
                        + "）。");
            }
        }
        return List.copyOf(contexts);
    }

    private String read(AgentEntity agent, String user, String service, String name, Target target,
            Map<String, Object> extra) {
        requireReadAction(agent, user, service, name);
        Map<String, Object> args = new LinkedHashMap<>(extra);
        args.put("owner", target.owner());
        args.put("repo", target.repo());
        if (name.equals("pull_request_read"))
            args.put("pullNumber", target.number());
        try {
            return broker.executeReadOnly(agent, user, service, name, JSON.writeValueAsString(args));
        } catch (BusinessException exception) {
            throw new BusinessException(
                    "PR 读取失败（" + name + "）：" + RunTimelineSanitizer.redactText(exception.getMessage()));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("PR 读取参数无效");
        }
    }

    private void requireReadAction(AgentEntity agent, String user, String service, String name) {
        RegisteredToolAction action = catalog.require(agent, user, service, name);
        if (!isReadAction(action) || !name.equals(action.name()))
            throw new BusinessException("PR 读取需要已安装、允许且明确归类只读的 GitHub 动作：" + name);
    }

    private boolean isReadAction(RegisteredToolAction action) {
        return "github".equals(action.serverName()) && action.explicitlyClassified()
                && action.classification() == ToolActionClassification.READ_ONLY;
    }

    private boolean hasAction(List<RegisteredToolAction> actions, String service, String name) {
        return actions.stream().anyMatch(
                action -> service.equals(action.serviceId()) && name.equals(action.name()) && isReadAction(action));
    }

    private JsonNode readJson(String response, String label) {
        if (response == null || response.length() > 2_000_000)
            throw new BusinessException(label + "返回为空或超过读取限制");
        try {
            JsonNode result = JSON.readTree(response);
            if (result == null)
                throw new BusinessException(label + "未返回有效数据");
            int status = result.path("status").asInt();
            if (status >= 400 && status <= 599)
                throw new BusinessException(label + "读取失败：GitHub HTTP " + status);
            return result;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(label + "的 MCP 返回格式不兼容");
        }
    }

    private String sha(JsonNode value) {
        if (!value.isTextual() || !value.asText().matches("[a-fA-F0-9]{40}"))
            throw new BusinessException("GitHub 未返回有效的固定提交 SHA");
        return value.asText().toLowerCase(Locale.ROOT);
    }

    private String text(String value, String label, List<String> gaps) {
        String redacted = RunTimelineSanitizer.redactText(value);
        if (redacted.length() > MAX_TEXT) {
            gaps.add(label + "：正文在 " + MAX_TEXT + " 字符处截断。");
            return redacted.substring(0, MAX_TEXT);
        }
        return redacted;
    }

    private String safePath(String value) {
        if (value.isBlank() || value.length() > 500 || value.startsWith("/") || value.contains("\\")
                || Arrays.asList(value.split("/")).contains("..") || value.chars().anyMatch(Character::isISOControl))
            throw new BusinessException("GitHub 返回了不合法文件路径");
        return value;
    }

    private Target parseTarget(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost()) || uri.getPort() != -1
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getRawPath().matches("/[A-Za-z0-9_.-]{1,39}/[A-Za-z0-9_.-]{1,100}/pull/[1-9][0-9]{0,8}/?"))
                throw new IllegalArgumentException();
            String[] parts = uri.getPath().split("/");
            String owner = parts[1].toLowerCase(Locale.ROOT);
            String repo = parts[2].toLowerCase(Locale.ROOT);
            if (List.of(".", "..").contains(owner) || List.of(".", "..").contains(repo))
                throw new IllegalArgumentException();
            int number = Integer.parseInt(parts[4]);
            return new Target(owner, repo, number);
        } catch (RuntimeException exception) {
            throw new BusinessException("请输入有效的 https://github.com/owner/repo/pull/编号 链接");
        }
    }

    private void requireUser(String user) {
        if (user == null || user.isBlank())
            throw new BusinessException("请先登录");
    }

    private record Target(String owner, String repo, int number) {
        String repository() {
            return owner + "/" + repo;
        }
        String url() {
            return "https://github.com/" + repository() + "/pull/" + number;
        }
    }

    public record InputChoice(String agentId, String agentName, String serviceId) {
    }

    public record InputOptions(GitHubConnectionDTO connection, List<InputChoice> choices) {
    }
}
