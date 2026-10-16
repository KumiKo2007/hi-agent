package org.xhy.infrastructure.repository.tool;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.xhy.domain.tool.repository.AgentToolActionSelectionStore;

/** 使用数据库保存动作允许列表，未选动作默认拒绝。 */
@Repository
public class JdbcAgentToolActionSelectionStore implements AgentToolActionSelectionStore {
    private final JdbcTemplate jdbc;

    public JdbcAgentToolActionSelectionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> list(String agentId, String serviceId) {
        return jdbc.query(
                "SELECT action_name FROM agent_tool_actions WHERE agent_id = ? AND service_id = ? "
                        + "ORDER BY action_name",
                (result, index) -> result.getString("action_name"), agentId, serviceId);
    }

    @Override
    public boolean contains(String agentId, String serviceId, String actionName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_tool_actions "
                        + "WHERE agent_id = ? AND service_id = ? AND action_name = ?",
                Integer.class, agentId, serviceId, actionName);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void replace(String agentId, String serviceId, List<String> actionNames) {
        jdbc.update("DELETE FROM agent_tool_actions WHERE agent_id = ? AND service_id = ?", agentId, serviceId);
        for (String actionName : actionNames) {
            jdbc.update("INSERT INTO agent_tool_actions (agent_id, service_id, action_name) VALUES (?, ?, ?)", agentId,
                    serviceId, actionName);
        }
    }
}
