package org.xhy.infrastructure.repository.run;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.xhy.application.run.service.RunAppService;
import org.xhy.application.run.service.RunRecoveryService;
import org.xhy.application.run.service.RunWorker;
import org.xhy.application.run.listener.RunWorkListener;
import org.xhy.domain.conversation.model.SessionEntity;
import org.xhy.domain.conversation.service.SessionDomainService;
import org.xhy.domain.run.constant.RunQueueNames;
import org.xhy.domain.run.repository.RunStore;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.infrastructure.auth.UserContext;
import org.xhy.infrastructure.mq.rabbit.RabbitRunDispatch;
import org.xhy.interfaces.api.portal.agent.PortalAgentRunController;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 在可用的 PostgreSQL/RabbitMQ 环境中检验公开 HTTP 到真实持久化和派发的路径。 */
@EnabledIfEnvironmentVariable(named = "RUN_DELIVERY_INTEGRATION", matches = "true")
class RunPostgresRabbitIntegrationTest {
    static final List<String> RUN_MIGRATION_RESOURCES = List.of("V20261015001__create_agent_runs.sql",
            "V20261015002__create_tool_action_policies.sql", "V20261015004__run_delivery_lease.sql",
            "V20261016001__pin_run_agent_version.sql", "V20261016008__pin_run_knowledge_version.sql",
            "V20261017002__persist_run_approvals.sql", "V20261017003__add_creator_approval_policies.sql",
            "V20261017004__persist_approved_external_resource.sql", "V20261017005__audit_run_reconciliation.sql",
            "V20261017006__add_reconciliation_evidence_references.sql", "V20261017007__pin_run_target_repository.sql",
            "V20261017008__add_run_timeline_events.sql");

    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void should_recoverAfterWorkerRestartAndIgnoreDuplicateMessages() throws Exception {
        CachingConnectionFactory rabbit = rabbitFactory();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(StoreConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            createSchema(dataSource);
            RunStore store = context.getBean(RunStore.class);
            RabbitAdmin admin = new RabbitAdmin(rabbit);
            DirectExchange exchange = new DirectExchange(RunQueueNames.EXCHANGE, true, false);
            Queue queue = new Queue(RunQueueNames.QUEUE, true);
            admin.declareExchange(exchange);
            admin.declareQueue(queue);
            admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(RunQueueNames.ROUTING_KEY));
            admin.purgeQueue(RunQueueNames.QUEUE, false);
            RabbitTemplate transport = new RabbitTemplate(rabbit);
            transport.setMessageConverter(new Jackson2JsonMessageConverter());
            RabbitRunDispatch dispatch = new RabbitRunDispatch(transport);

            SessionDomainService sessions = mock(SessionDomainService.class);
            SessionEntity session = new SessionEntity();
            session.setId("session-1");
            session.setAgentId("agent-1");
            when(sessions.getSession("session-1", "owner")).thenReturn(session);
            MockMvc api = MockMvcBuilders
                    .standaloneSetup(new PortalAgentRunController(new RunAppService(sessions, store, dispatch)))
                    .build();
            UserContext.setCurrentUserId("owner");

            String runId = postGoal(api, "整理资料").path("id").asText();
            assertEquals("PENDING", detail(api, runId).path("status").asText());
            assertEquals(runId, transport.receiveAndConvert(RunQueueNames.QUEUE, 5000));

            // 工作者在领取后消失，数据库租约过期，恢复扫描重新派发。
            assertEquals(true, store.claim(runId, "crashed-worker", 90));
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE agent_runs SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?")) {
                statement.setString(1, runId);
                statement.executeUpdate();
            }
            new RunRecoveryService(store, dispatch).recover();
            assertEquals(runId, transport.receiveAndConvert(RunQueueNames.QUEUE, 5000));

            AtomicInteger calls = new AtomicInteger();
            RunWorker restarted = new RunWorker(store, run -> {
                calls.incrementAndGet();
                return "已整理：" + run.getGoal();
            });
            new RunWorkListener(restarted).receive(runId);
            dispatch.dispatch(runId);
            assertEquals(runId, transport.receiveAndConvert(RunQueueNames.QUEUE, 5000));
            new RunWorkListener(restarted).receive(runId);

            // 新的控制器重新从数据库读取同一运行，等价于浏览器刷新。
            MockMvc reloaded = MockMvcBuilders
                    .standaloneSetup(new PortalAgentRunController(new RunAppService(sessions, store, dispatch)))
                    .build();
            JsonNode result = detail(reloaded, runId);
            assertEquals("COMPLETED", result.path("status").asText());
            assertEquals("已整理：整理资料", result.path("result").asText());
            assertEquals("COMPLETED", result.path("steps").get(0).path("status").asText());
            assertEquals(1, calls.get());

            // 两个工作者收到同一运行时，只有一个可进入模型。
            String concurrentId = postGoal(api, "并发目标").path("id").asText();
            assertEquals(concurrentId, transport.receiveAndConvert(RunQueueNames.QUEUE, 5000));
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicInteger concurrentCalls = new AtomicInteger();
            RunWorker first = new RunWorker(store, run -> {
                concurrentCalls.incrementAndGet();
                firstStarted.countDown();
                try {
                    if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("测试模型等待超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return "唯一结果";
            });
            Thread firstThread = new Thread(() -> new RunWorkListener(first).receive(concurrentId));
            firstThread.start();
            assertEquals(true, firstStarted.await(5, TimeUnit.SECONDS));
            RunWorker duplicate = new RunWorker(store, run -> {
                concurrentCalls.incrementAndGet();
                return "冲突结果";
            });
            new RunWorkListener(duplicate).receive(concurrentId);
            releaseFirst.countDown();
            firstThread.join(5000);
            assertEquals(1, concurrentCalls.get());
            assertEquals("唯一结果", detail(api, concurrentId).path("result").asText());
        } finally {
            rabbit.destroy();
        }
    }

    @Test
    void beginApprovalAndCancellationSerializeAndPersistedExternalResultsCannotBeCancelled() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(StoreConfig.class)) {
            DataSource dataSource = context.getBean(DataSource.class);
            createSchema(dataSource);
            RunStore store = context.getBean(RunStore.class);

            RunEntity racingRun = approvedPendingRun(store);
            String racingApprovalId = store.findApprovalForRun(racingRun.getId()).getId();
            try (Connection lock = dataSource.getConnection();
                    PreparedStatement lockRun = lock
                            .prepareStatement("SELECT id FROM agent_runs WHERE id = ? FOR UPDATE")) {
                lock.setAutoCommit(false);
                lockRun.setString(1, racingRun.getId());
                lockRun.executeQuery().close();
                CyclicBarrier startTogether = new CyclicBarrier(3);
                FutureTask<Boolean> begin = new FutureTask<>(() -> {
                    startTogether.await();
                    return store.beginApprovedAction(racingRun.getId(), "approved-worker", racingApprovalId);
                });
                FutureTask<Boolean> cancel = new FutureTask<>(() -> {
                    startTogether.await();
                    return store.cancelOwned(racingRun.getId(), "owner");
                });
                Thread beginThread = new Thread(begin, "begin-approved-action-test");
                Thread cancelThread = new Thread(cancel, "cancel-approved-run-test");
                beginThread.start();
                cancelThread.start();
                startTogether.await();
                lock.commit();

                boolean began = begin.get(10, TimeUnit.SECONDS);
                boolean cancelled = cancel.get(10, TimeUnit.SECONDS);
                assertFalse(began && cancelled, "cancellation and external action start must not both win");
                assertTrue(began || cancelled, "one serialized transition should win");
            }

            RunEntity persistedRun = approvedPendingRun(store);
            String persistedApprovalId = store.findApprovalForRun(persistedRun.getId()).getId();
            assertTrue(store.beginApprovedAction(persistedRun.getId(), "approved-worker", persistedApprovalId));
            assertTrue(store.recordApprovedActionResult(persistedRun.getId(), "approved-worker", persistedApprovalId,
                    "issue-42"));
            assertFalse(store.cancelOwned(persistedRun.getId(), "owner"));
            assertEquals("RUNNING", store.findOwned(persistedRun.getId(), "owner").getStatus());
            assertEquals("EXECUTED", store.findApprovalForRun(persistedRun.getId()).getStatus());

            RunEntity unresolvedRun = approvedPendingRun(store);
            String unresolvedApprovalId = store.findApprovalForRun(unresolvedRun.getId()).getId();
            assertTrue(store.beginApprovedAction(unresolvedRun.getId(), "approved-worker", unresolvedApprovalId));
            assertTrue(store.waitForReconciliation(unresolvedRun.getId(), "approved-worker"));
            assertFalse(store.cancelOwned(unresolvedRun.getId(), "owner"));
            assertEquals("WAITING_RECONCILIATION", store.findOwned(unresolvedRun.getId(), "owner").getStatus());
            assertEquals("RECONCILIATION_REQUIRED", store.findApprovalForRun(unresolvedRun.getId()).getStatus());
        }
    }

    private RunEntity approvedPendingRun(RunStore store) {
        RunEntity run = RunEntity.create("owner", "session-approval", "agent-approval", "Create issue");
        RunApprovalEntity approval = RunApprovalEntity.create(run.getId(), new RunApprovalDraft("service-1",
                "create_issue", "Create issue", "owner/repo", "{}", Map.of("type", "object")));
        store.createRunWithStep(run, RunStepEntity.modelCall(run.getId()));
        assertTrue(store.claim(run.getId(), "initial-worker", 90));
        assertTrue(store.requestApproval(run.getId(), "initial-worker", approval));
        assertTrue(store.decideApproval(run.getId(), approval.getId(), "owner", true));
        assertTrue(store.claim(run.getId(), "approved-worker", 90));
        return run;
    }

    private CachingConnectionFactory rabbitFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory("localhost",
                Integer.parseInt(System.getenv().getOrDefault("RUN_TEST_RABBIT_PORT", "55672")));
        factory.setUsername("guest");
        factory.setPassword("guest");
        return factory;
    }

    private void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            // 运行迁移引用既有 Agent 与 MCP 服务身份；专用空库只需这两个主键前置表。
            // Run 的表、约束及索引仍全部由真实迁移创建，不在夹具中复制。
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS agents (id VARCHAR(36) PRIMARY KEY)");
                statement.execute("CREATE TABLE IF NOT EXISTS tools (id VARCHAR(36) PRIMARY KEY)");
            }
            for (String migration : RUN_MIGRATION_RESOURCES) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + migration));
            }
        }
    }

    private JsonNode postGoal(MockMvc api, String goal) throws Exception {
        String body = json.writeValueAsString(Map.of("goal", goal));
        String response = api
                .perform(post("/agents/sessions/session-1/runs").contentType("application/json").content(body))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode result = json.readTree(response);
        assertEquals(200, result.path("code").asInt(), response);
        assertFalse(result.path("data").path("id").asText().isBlank());
        return result.path("data");
    }

    private JsonNode detail(MockMvc api, String runId) throws Exception {
        String response = api.perform(get("/agents/runs/" + runId)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode result = json.readTree(response);
        assertEquals(200, result.path("code").asInt(), response);
        return result.path("data");
    }

    @Configuration
    @EnableTransactionManagement
    static class StoreConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setDriverClassName("org.postgresql.Driver");
            source.setUrl(
                    System.getenv().getOrDefault("RUN_TEST_DB_URL", "jdbc:postgresql://localhost:55432/agentx_test"));
            source.setUsername("postgres");
            source.setPassword("postgres");
            return source;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            GlobalConfig global = new GlobalConfig();
            GlobalConfig.DbConfig database = new GlobalConfig.DbConfig();
            database.setLogicDeleteField("deletedAt");
            database.setLogicDeleteValue("now()");
            database.setLogicNotDeleteValue("null");
            global.setDbConfig(database);
            GlobalConfigUtils.setGlobalConfig(configuration, global);
            configuration.addMapper(RunMapper.class);
            configuration.addMapper(RunStepMapper.class);
            configuration.addMapper(RunApprovalMapper.class);
            configuration.addMapper(RunReconciliationMapper.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean
        RunMapper runMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RunMapper.class);
        }

        @Bean
        RunStepMapper stepMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RunStepMapper.class);
        }

        @Bean
        RunApprovalMapper approvalMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RunApprovalMapper.class);
        }

        @Bean
        RunReconciliationMapper reconciliationMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(RunReconciliationMapper.class);
        }

        @Bean
        RunStore runStore(RunMapper runs, RunStepMapper steps, RunApprovalMapper approvals,
                RunReconciliationMapper reconciliations) {
            return new MyBatisRunStore(runs, steps, approvals, reconciliations);
        }
    }
}
