package org.xhy.interfaces.api.portal.review;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.xhy.application.github.service.*;
import org.xhy.application.review.service.PrInputAppService;
import org.xhy.application.tool.service.*;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.repository.AgentRepository;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.tool.model.UserToolEntity;
import org.xhy.domain.tool.repository.UserToolRepository;
import org.xhy.domain.tool.service.UserToolDomainService;
import org.xhy.domain.tool.model.ToolActionPolicy;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.config.GlobalExceptionHandler;
import org.xhy.infrastructure.github.GitHubTokenCipher;
import org.xhy.infrastructure.repository.github.*;
import org.xhy.infrastructure.repository.review.JdbcPrInputStore;
import org.xhy.infrastructure.repository.tool.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** 实际 PR controller/app、MyBatis/JDBC 持久化、个人授权与 broker 的本地 HTTP slice。 只替换外部 GitHub/MCP，使用预置 UserContext 测试身份；不宣称验证 JWT
 * 或完整应用启动。 */
public class PrInputBrowserHarness {
    public static void main(String[] args) throws Exception {
        String url = Objects.requireNonNull(System.getenv("PR_INPUT_POSTGRES_URL"), "需要独立测试 PostgreSQL URL");
        String user = System.getenv("PR_INPUT_POSTGRES_USER");
        String password = System.getenv("PR_INPUT_POSTGRES_PASSWORD");
        String schema = "pr_browser_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        admin.execute("CREATE SCHEMA " + schema);
        DriverManagerDataSource database = new DriverManagerDataSource(
                url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password);
        JdbcTemplate jdbc = new JdbcTemplate(database);
        createEntityTable(jdbc, "agents", AgentEntity.class);
        createEntityTable(jdbc, "user_tools", UserToolEntity.class);
        jdbc.execute("CREATE TABLE tools (id VARCHAR(36) PRIMARY KEY)");
        jdbc.update("INSERT INTO tools(id) VALUES ('github-service')");
        for (String migration : List.of("V20261015002__create_tool_action_policies.sql",
                "V20261015011__create_github_connections.sql", "V20261020020__create_pr_input_snapshots.sql")) {
            try (Connection connection = database.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + migration));
            }
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig global = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        dbConfig.setLogicDeleteField("deletedAt");
        dbConfig.setLogicDeleteValue("now()");
        dbConfig.setLogicNotDeleteValue("null");
        global.setDbConfig(dbConfig);
        GlobalConfigUtils.setGlobalConfig(configuration, global);
        configuration.addMapper(AgentRepository.class);
        configuration.addMapper(UserToolRepository.class);
        configuration.addMapper(GitHubConnectionMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(database);
        factory.setConfiguration(configuration);
        SqlSessionTemplate sessions = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        AgentRepository agents = sessions.getMapper(AgentRepository.class);
        UserToolRepository tools = sessions.getMapper(UserToolRepository.class);
        AgentEntity agent = new AgentEntity();
        agent.setId("agent");
        agent.setUserId("owner");
        agent.setName("代码助理");
        agent.setEnabled(true);
        agent.setToolIds(List.of("github-service"));
        agents.insert(agent);
        ControlledGitHubMcp remote = new ControlledGitHubMcp();
        UserToolEntity installed = new UserToolEntity();
        installed.setId("installed");
        installed.setUserId("owner");
        installed.setToolId("github-service");
        installed.setName("GitHub");
        installed.setMcpServerName("github");
        installed.setToolList(List.of(ControlledGitHubMcp.definition("pull_request_read", remote.prSchema),
                ControlledGitHubMcp.definition("get_file_contents", remote.fileSchema)));
        tools.insert(installed);
        ObjectMapper json = new ObjectMapper();
        JdbcToolActionPolicyStore policies = new JdbcToolActionPolicyStore(jdbc, json);
        policies.save(new ToolActionPolicy("github-service", "pull_request_read", ToolActionClassification.READ_ONLY,
                remote.prSchema));
        policies.save(new ToolActionPolicy("github-service", "get_file_contents", ToolActionClassification.READ_ONLY,
                remote.fileSchema));
        JdbcAgentToolActionSelectionStore selections = new JdbcAgentToolActionSelectionStore(jdbc);
        selections.replace("agent", "github-service", List.of("pull_request_read", "get_file_contents"));
        GitHubConnectionAppService github = new GitHubConnectionAppService(
                new MyBatisGitHubConnectionRepository(sessions.getMapper(GitHubConnectionMapper.class), json),
                (token, repos) -> new org.xhy.infrastructure.github.GitHubCredentialVerifier.Identity("1", "alice"),
                new GitHubTokenCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        github.connect("owner", ControlledGitHubMcp.TOKEN, List.of("alice/repo"));
        ToolActionCatalog catalog = new PersistentToolActionCatalog(new UserToolDomainService(tools), policies,
                selections);
        McpClientFactory clients = remote.factory();
        ToolBroker broker = new ToolBroker(catalog, clients, new GitHubMcpActionClientProvider(clients, github),
                List.of(new GitHubMcpToolActionAdapter()));
        PrInputAppService inputs = new PrInputAppService(new AgentDomainService(agents, null, null, null), catalog,
                broker, github, new JdbcPrInputStore(jdbc, json));
        MockMvc api = MockMvcBuilders.standaloneSetup(new PrInputController(inputs))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",
                Integer.parseInt(System.getenv().getOrDefault("PR_INPUT_HTTP_PORT", "18837"))), 0);
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
            if (!("Bearer " + ownerToken()).equals(auth) && !("Bearer " + otherToken()).equals(auth)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            UserContext.setCurrentUserId(("Bearer " + ownerToken()).equals(auth) ? "owner" : "other");
            try {
                var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(exchange.getRequestMethod()),
                        URI.create(exchange.getRequestURI().toString().substring(4)));
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType != null)
                    request.contentType(contentType);
                request.content(exchange.getRequestBody().readAllBytes());
                var response = api.perform(request).andReturn().getResponse();
                exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                byte[] bytes = response.getContentAsByteArray();
                exchange.sendResponseHeaders(response.getStatus(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception exception) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                UserContext.clear();
                exchange.close();
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }));
        server.start();
        System.out.println("PR_INPUT_BROWSER_HARNESS_READY");
        new CountDownLatch(1).await();
    }

    static String ownerToken() {
        return token("owner");
    }
    static String otherToken() {
        return token("other");
    }
    static String token(String user) {
        return "e30."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(
                        ("{\"userId\":\"" + user + "\",\"exp\":2540188800}").getBytes(StandardCharsets.UTF_8))
                + ".pr-browser-test";
    }

    /** 测试独立 schema 的 ORM 实体表；线上新快照表仍使用正式迁移。 */
    static void createEntityTable(JdbcTemplate jdbc, String table, Class<?> type) {
        List<String> columns = new ArrayList<>();
        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                TableField mapping = field.getAnnotation(TableField.class);
                if (Modifier.isStatic(field.getModifiers()) || (mapping != null && !mapping.exist()))
                    continue;
                String name = mapping != null && !mapping.value().isBlank()
                        ? mapping.value()
                        : field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
                String sqlType = field.getType() == Boolean.class
                        ? "BOOLEAN"
                        : field.getType() == LocalDateTime.class ? "TIMESTAMP" : "TEXT";
                columns.add(name + " " + sqlType + (name.equals("id") ? " PRIMARY KEY" : ""));
            }
        }
        jdbc.execute("CREATE TABLE " + table + " (" + String.join(",", columns) + ")");
    }
}
