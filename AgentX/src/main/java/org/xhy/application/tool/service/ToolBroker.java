package org.xhy.application.tool.service;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.xhy.application.tool.model.RegisteredToolAction;
import org.xhy.application.tool.model.McpActionResult;
import org.xhy.domain.run.model.RunApprovalDraft;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.infrastructure.exception.BusinessException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;

/** 模型提出动作后唯一允许调用 MCP 的边界。 */
@Service
public class ToolBroker {
    private static final Logger logger = LoggerFactory.getLogger(ToolBroker.class);

    private final ToolActionCatalog catalog;
    private final McpActionClientProvider actionClients;
    private final List<McpToolActionAdapter> actionAdapters;

    public ToolBroker(ToolActionCatalog catalog, McpClientFactory clients) {
        this(catalog, clients, (serverName, userId, targetRepository) -> clients.open(serverName, userId), List.of());
    }

    public ToolBroker(ToolActionCatalog catalog, McpClientFactory clients,
            McpActionClientProvider actionClients) {
        this(catalog, clients, actionClients, List.of());
    }

    @Autowired
    public ToolBroker(ToolActionCatalog catalog, McpClientFactory clients,
            McpActionClientProvider actionClients, List<McpToolActionAdapter> actionAdapters) {
        this.catalog = catalog;
        this.actionClients = actionClients;
        this.actionAdapters = actionAdapters == null ? List.of() : List.copyOf(actionAdapters);
    }

    /** Tool schemas the model may request; write executors remain behind owner approval. */
    public List<ModelTool> listModelTools(AgentEntity agent, String userId) {
        return listModelTools(agent, userId, null);
    }

    /** Lists only actions allowed by their connector adapter and validates each live schema. */
    public List<ModelTool> listModelTools(AgentEntity agent, String userId, String targetRepository) {
        List<ModelTool> result = new ArrayList<>();
        Set<String> aliases = new HashSet<>();
        for (RegisteredToolAction action : catalog.list(agent, userId)) {
            if (action.schema() == null) {
                throw new McpSchemaMismatchException();
            }
            Map<String, Object> snapshot = McpSchema.snapshotSchema(action.schema());
            if (!isVisible(action, targetRepository)) {
                continue;
            }
            ToolSpecification live = findLiveTool(action, userId, targetRepository);
            Map<String, Object> liveSchema = McpSchema.fromRuntime(live.parameters());
            if (!McpSchema.sameSchema(snapshot, liveSchema)) {
                throw new McpSchemaMismatchException();
            }
            String alias = alias(action);
            if (!aliases.add(alias)) {
                throw new BusinessException("MCP 工具动作名称冲突，执行已暂停");
            }
            ToolSpecification exposed = ToolSpecification.builder().name(alias)
                    .description(action.description() == null ? "Registered MCP action" : action.description())
                    .parameters(live.parameters()).build();
            result.add(new ModelTool(alias, action, exposed, snapshot));
        }
        return List.copyOf(result);
    }

    /** Rechecks the mutable creator policy immediately before an automatic write is sent. */
    public boolean hasCurrentCreatorPreauthorization(AgentEntity agent, String userId, String serviceId,
            String actionName) {
        RegisteredToolAction action = catalog.require(agent, userId, serviceId, actionName);
        return action.explicitlyClassified()
                && action.classification() == ToolActionClassification.WRITE_REQUIRES_APPROVAL
                && action.creatorPreauthorized();
    }

    /** Validates a model-proposed write and freezes the canonical parameters shown to the owner. */
    public RunApprovalDraft prepareForApproval(AgentEntity agent, String userId, String serviceId, String actionName,
            String arguments) {
        RegisteredToolAction action = catalog.require(agent, userId, serviceId, actionName);
        return prepareForApproval(agent, userId, serviceId, actionName, arguments, action.schema());
    }

    /** Revalidates against the exact schema exposed in this run's model request. */
    public RunApprovalDraft prepareForApproval(AgentEntity agent, String userId, String serviceId, String actionName,
            String arguments, Map<String, Object> modelSchemaSnapshot) {
        return prepareForApproval(agent, userId, serviceId, actionName, arguments, modelSchemaSnapshot, null);
    }

    /** Revalidates and checks a run-pinned target before opening the live remote action schema. */
    public RunApprovalDraft prepareForApproval(AgentEntity agent, String userId, String serviceId, String actionName,
            String arguments, Map<String, Object> modelSchemaSnapshot, String expectedTargetRepository) {
        Map<String, Object> snapshot = McpSchema.snapshotSchema(modelSchemaSnapshot);
        RegisteredToolAction action = requireSnapshotAction(agent, userId, serviceId, actionName, snapshot);
        if (action.classification() == ToolActionClassification.READ_ONLY) {
            throw new BusinessException("只读动作无需写入审批");
        }
        String validatedArguments = McpSchema.validateArguments(snapshot, arguments);
        String targetRepository = resolveTargetRepository(action, validatedArguments);
        if (expectedTargetRepository != null && targetRepository != null
                && !expectedTargetRepository.equals(targetRepository)) {
            throw new BusinessException("写入目标与用户选择的仓库不一致，已停止外部写入");
        }
        ToolSpecification live = findLiveTool(action, userId, targetRepository);
        if (!McpSchema.sameSchema(snapshot, McpSchema.fromRuntime(live.parameters()))) {
            throw new McpSchemaMismatchException();
        }
        String intentId = UUID.randomUUID().toString();
        validatedArguments = prepareApprovalArguments(action, snapshot, validatedArguments, intentId);
        return new RunApprovalDraft(serviceId, actionName, action.description(), targetRepository, validatedArguments,
                snapshot, intentId);
    }

    /** Executes the persisted approval snapshot after checking it against today's action and MCP schema. */
    public McpActionResult executeApprovedWrite(AgentEntity agent, String userId, RunApprovalEntity approval) {
        return executeApprovedWrite(agent, userId, approval, () -> {
        });
    }

    /** Executes a persisted write snapshot through the generic action-client boundary. */
    public McpActionResult executeApprovedWrite(AgentEntity agent, String userId, RunApprovalEntity approval,
            Runnable beforeExternalCall) {
        if (approval == null || !"APPROVED".equals(approval.getStatus())) {
            throw new BusinessException("外部写动作尚未获得使用者批准");
        }
        Map<String, Object> snapshot = McpSchema.readSnapshot(approval.getSchemaJson());
        RegisteredToolAction action = requireSnapshotAction(agent, userId, approval.getServiceId(),
                approval.getActionName(), snapshot);
        if (action.classification() == ToolActionClassification.READ_ONLY) {
            throw new BusinessException("工具动作安全分类已变化，执行已暂停");
        }
        if ("CREATOR_PREAUTHORIZATION".equals(approval.getApprovalSource()) && (!action.explicitlyClassified()
                || action.classification() != ToolActionClassification.WRITE_REQUIRES_APPROVAL
                || !action.creatorPreauthorized())) {
            throw new BusinessException("创建者预授权已撤销，执行已暂停");
        }
        String validatedArguments = McpSchema.validateArguments(snapshot, approval.getArguments());
        if (!validatedArguments.equals(approval.getArguments())) {
            throw new BusinessException("已批准的工具参数已变化，需要重新审批");
        }
        String currentTarget = resolveTargetRepository(action, validatedArguments);
        if (!Objects.equals(currentTarget, approval.getTargetRepository())) {
            throw new BusinessException("已批准的目标仓库已变化，需要重新审批");
        }
        try (McpClient client = openClient(action, userId, currentTarget)) {
            ToolSpecification live = client.listTools().stream().filter(spec -> action.name().equals(spec.name()))
                    .findFirst().orElseThrow(McpSchemaMismatchException::new);
            if (!McpSchema.sameSchema(snapshot, McpSchema.fromRuntime(live.parameters()))) {
                throw new McpSchemaMismatchException();
            }
            beforeExternalCall.run();
            String result = client.executeTool(
                    ToolExecutionRequest.builder().name(action.name()).arguments(validatedArguments).build());
            logger.info("已批准 MCP 写动作执行完成: serviceId={}, action={}", action.serviceId(), action.name());
            return new McpActionResult(action.serverName(), action.name(), currentTarget, result);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("已批准的 MCP 写动作执行失败");
        }
    }

    private ToolSpecification findLiveTool(RegisteredToolAction action, String userId, String targetRepository) {
        try (McpClient client = openClient(action, userId, targetRepository)) {
            return client.listTools().stream().filter(spec -> action.name().equals(spec.name())).findFirst()
                    .orElseThrow(McpSchemaMismatchException::new);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("MCP 工具动作目录读取失败");
        }
    }

    private String alias(RegisteredToolAction action) {
        String key = action.serviceId() + ":" + action.name();
        return "mcp_" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private RegisteredToolAction requireSnapshotAction(AgentEntity agent, String userId, String serviceId,
            String actionName, Map<String, Object> snapshot) {
        RegisteredToolAction action;
        try {
            action = catalog.require(agent, userId, serviceId, actionName);
        } catch (BusinessException exception) {
            throw new McpSchemaMismatchException();
        }
        if (action.schema() == null || !McpSchema.sameSchema(snapshot, action.schema())) {
            throw new McpSchemaMismatchException();
        }
        return action;
    }

    public record ModelTool(String exposedName, RegisteredToolAction action, ToolSpecification specification,
            Map<String, Object> schemaSnapshot) {
        public ModelTool {
            schemaSnapshot = McpSchema.snapshotSchema(schemaSnapshot);
        }
    }

    /** 只读动作先校验登记、分类、参数与远端实际结构，然后才调用 MCP。 */
    public String executeReadOnly(AgentEntity agent, String userId, String serviceId, String actionName,
            String arguments) {
        RegisteredToolAction action = catalog.require(agent, userId, serviceId, actionName);
        return executeReadOnly(agent, userId, serviceId, actionName, arguments, action.schema());
    }

    /** Validates model-proposed reads against the exact registered schema exposed in the request. */
    public String executeReadOnly(AgentEntity agent, String userId, String serviceId, String actionName,
            String arguments, Map<String, Object> modelSchemaSnapshot) {
        Map<String, Object> schema = McpSchema.snapshotSchema(modelSchemaSnapshot);
        RegisteredToolAction action = requireSnapshotAction(agent, userId, serviceId, actionName, schema);
        if (action.classification() != ToolActionClassification.READ_ONLY) {
            throw new BusinessException("外部写动作需要审批，不能通过只读边界执行");
        }
        String validatedArguments = McpSchema.validateArguments(schema, arguments);
        String targetRepository = resolveTargetRepository(action, validatedArguments);
        try (McpClient client = openClient(action, userId, targetRepository)) {
            ToolSpecification live = client.listTools().stream().filter(spec -> actionName.equals(spec.name()))
                    .findFirst().orElseThrow(McpSchemaMismatchException::new);
            if (!McpSchema.sameSchema(schema, McpSchema.fromRuntime(live.parameters()))) {
                throw new McpSchemaMismatchException();
            }
            String result = client
                    .executeTool(ToolExecutionRequest.builder().name(actionName).arguments(validatedArguments).build());
            logger.info("只读工具动作执行完成: serviceId={}, action={}", serviceId, actionName);
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            logger.warn("只读工具动作执行失败: serviceId={}, action={}, causeType={}", serviceId, actionName,
                    exception.getClass().getSimpleName());
            throw new BusinessException("MCP 工具动作执行失败");
        }
    }

    private McpClient openClient(RegisteredToolAction action, String userId, String targetRepository) {
        return actionClients.open(action.serverName(), userId, targetRepository);
    }

    private boolean isVisible(RegisteredToolAction action, String selectedTargetRepository) {
        return actionAdapters.stream().filter(adapter -> adapter.supports(action))
                .allMatch(adapter -> adapter.isVisible(action, selectedTargetRepository));
    }

    private String resolveTargetRepository(RegisteredToolAction action, String arguments) {
        String resolved = null;
        for (McpToolActionAdapter adapter : actionAdapters) {
            if (!adapter.supports(action)) {
                continue;
            }
            String candidate = adapter.resolveTargetRepository(action, arguments);
            if (candidate != null && resolved != null && !resolved.equals(candidate)) {
                throw new BusinessException("MCP 动作适配器返回了不一致的目标仓库");
            }
            if (candidate != null) {
                resolved = candidate;
            }
        }
        return resolved;
    }

    private String prepareApprovalArguments(RegisteredToolAction action, Map<String, Object> schema,
            String arguments, String intentId) {
        String prepared = arguments;
        for (McpToolActionAdapter adapter : actionAdapters) {
            if (adapter.supports(action)) {
                prepared = adapter.prepareApprovalArguments(action, schema, prepared, intentId);
            }
        }
        return prepared;
    }
}
