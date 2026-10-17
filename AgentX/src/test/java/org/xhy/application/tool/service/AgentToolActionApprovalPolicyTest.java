package org.xhy.application.tool.service;

import org.junit.jupiter.api.Test;
import org.xhy.domain.agent.model.AgentEntity;
import org.xhy.domain.agent.service.AgentDomainService;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.domain.tool.model.ToolActionPolicy;
import org.xhy.domain.tool.model.UserToolEntity;
import org.xhy.domain.tool.model.config.ToolDefinition;
import org.xhy.domain.tool.repository.AgentToolActionSelectionStore;
import org.xhy.domain.tool.repository.ToolActionPolicyStore;
import org.xhy.domain.tool.service.UserToolDomainService;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentToolActionApprovalPolicyTest {
    private static final Map<String, Object> SCHEMA = Map.of("type", "object");

    @Test
    void shouldAllowCreatorToPreauthorizeOnlySelectedExplicitWriteActions() {
        Fixture fixture = new Fixture(ToolActionClassification.WRITE_REQUIRES_APPROVAL);

        assertEquals(List.of("create_issue"), fixture.service.replacePreauthorized(
                "agent-1", "service-1", List.of("create_issue"), "creator-1"));
        assertEquals(List.of("create_issue"), fixture.selections.saved);
    }

    @Test
    void shouldRejectPreauthorizationForUnclassifiedActions() {
        Fixture fixture = new Fixture(null);

        assertThrows(BusinessException.class, () -> fixture.service.replacePreauthorized(
                "agent-1", "service-1", List.of("create_issue"), "creator-1"));
        assertEquals(List.of(), fixture.selections.saved);
    }

    @Test
    void shouldRejectPreauthorizationForReadOnlyActionsAndAllowCreatorToRevokeIt() {
        Fixture fixture = new Fixture(ToolActionClassification.READ_ONLY);

        assertThrows(BusinessException.class, () -> fixture.service.replacePreauthorized(
                "agent-1", "service-1", List.of("create_issue"), "creator-1"));

        Fixture writable = new Fixture(ToolActionClassification.WRITE_REQUIRES_APPROVAL);
        assertEquals(List.of(), writable.service.replacePreauthorized("agent-1", "service-1", List.of(), "creator-1"));
        assertEquals(List.of(), writable.selections.saved);
    }

    @Test
    void shouldKeepUnclassifiedActionsApprovalRequiredEvenIfAStalePreauthorizationExists() {
        AgentEntity agent = new AgentEntity();
        agent.setId("agent-1");
        agent.setToolIds(List.of("service-1"));
        ToolDefinition definition = new ToolDefinition();
        definition.setName("create_issue");
        UserToolEntity installed = new UserToolEntity();
        installed.setMcpServerName("github");
        installed.setToolList(List.of(definition));
        UserToolDomainService installedTools = new UserToolDomainService(null) {
            @Override
            public UserToolEntity findByToolIdAndUserId(String toolId, String userId) {
                return installed;
            }
        };
        ToolActionPolicyStore policies = new ToolActionPolicyStore() {
            @Override public ToolActionPolicy find(String serviceId, String actionName) { return null; }
            @Override public void save(ToolActionPolicy policy) { throw new UnsupportedOperationException(); }
        };
        TestSelectionStore selections = new TestSelectionStore();
        selections.selected = List.of("create_issue");
        selections.saved = List.of("create_issue");
        PersistentToolActionCatalog catalog = new PersistentToolActionCatalog(installedTools, policies, selections);

        var action = catalog.require(agent, "user-1", "service-1", "create_issue");

        assertEquals(ToolActionClassification.WRITE_REQUIRES_APPROVAL, action.classification());
        assertEquals(false, action.explicitlyClassified());
        assertEquals(false, action.creatorPreauthorized());
    }

    private static final class Fixture {
        private final TestSelectionStore selections = new TestSelectionStore();
        private final AgentToolActionAppService service;

        private Fixture(ToolActionClassification classification) {
            AgentEntity agent = new AgentEntity();
            agent.setId("agent-1");
            agent.setUserId("creator-1");
            agent.setToolIds(List.of("service-1"));
            AgentDomainService agents = new AgentDomainService(null, null, null, null) {
                @Override
                public AgentEntity getAgent(String agentId, String userId) {
                    return agent;
                }
            };

            ToolDefinition action = new ToolDefinition();
            action.setName("create_issue");
            action.setEnabled(true);
            UserToolEntity installed = new UserToolEntity();
            installed.setToolId("service-1");
            installed.setToolList(List.of(action));
            UserToolDomainService installedTools = new UserToolDomainService(null) {
                @Override
                public UserToolEntity findByToolIdAndUserId(String toolId, String userId) {
                    return installed;
                }
            };

            ToolActionPolicyStore policies = new ToolActionPolicyStore() {
                @Override
                public ToolActionPolicy find(String serviceId, String actionName) {
                    return classification == null ? null
                            : new ToolActionPolicy(serviceId, actionName, classification, SCHEMA);
                }

                @Override
                public void save(ToolActionPolicy policy) {
                    throw new UnsupportedOperationException();
                }
            };
            selections.selected = List.of("create_issue");
            selections.saved = classification == ToolActionClassification.WRITE_REQUIRES_APPROVAL
                    ? List.of("create_issue") : List.of();
            service = new AgentToolActionAppService(agents, installedTools, selections, policies);
        }
    }

    private static final class TestSelectionStore implements AgentToolActionSelectionStore {
        private List<String> selected = new ArrayList<>();
        private List<String> saved = new ArrayList<>();

        @Override public List<String> list(String agentId, String serviceId) { return selected; }
        @Override public boolean contains(String agentId, String serviceId, String actionName) {
            return selected.contains(actionName);
        }
        @Override public void replace(String agentId, String serviceId, List<String> actionNames) {
            selected = new ArrayList<>(actionNames);
        }
        @Override public List<String> preauthorized(String agentId, String serviceId) { return saved; }
        @Override public boolean isPreauthorized(String agentId, String serviceId, String actionName) {
            return saved.contains(actionName);
        }
        @Override public void replacePreauthorized(String agentId, String serviceId, List<String> actionNames) {
            saved = new ArrayList<>(actionNames);
        }
        @Override public void revokePreauthorization(String serviceId, String actionName) {
            saved.remove(actionName);
        }
    }
}
