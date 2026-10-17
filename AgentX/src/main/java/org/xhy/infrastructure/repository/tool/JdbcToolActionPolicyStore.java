package org.xhy.infrastructure.repository.tool;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.xhy.domain.tool.constant.ToolActionClassification;
import org.xhy.domain.tool.model.ToolActionPolicy;
import org.xhy.domain.tool.repository.ToolActionPolicyStore;
import org.xhy.infrastructure.exception.BusinessException;

/** PostgreSQL 保存平台审核的动作分类，不信任 MCP 服务自报分类。 */
@Repository
public class JdbcToolActionPolicyStore implements ToolActionPolicyStore {
    private static final TypeReference<Map<String, Object>> SCHEMA_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcToolActionPolicyStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public ToolActionPolicy find(String serviceId, String actionName) {
        return jdbc.query("SELECT classification, schema_json FROM tool_action_policies "
                + "WHERE service_id = ? AND action_name = ?", result -> {
                    if (!result.next()) {
                        return null;
                    }
                    try {
                        return new ToolActionPolicy(serviceId, actionName,
                                ToolActionClassification.valueOf(result.getString("classification")),
                                json.readValue(result.getString("schema_json"), SCHEMA_TYPE));
                    } catch (JsonProcessingException exception) {
                        throw new BusinessException("工具动作结构记录损坏");
                    }
                }, serviceId, actionName);
    }

    @Override
    public void save(ToolActionPolicy policy) {
        try {
            jdbc.update(
                    "INSERT INTO tool_action_policies (service_id, action_name, classification, schema_json) "
                            + "VALUES (?, ?, ?, ?::jsonb) ON CONFLICT (service_id, action_name) DO UPDATE "
                            + "SET classification = EXCLUDED.classification, schema_json = EXCLUDED.schema_json, "
                            + "updated_at = CURRENT_TIMESTAMP",
                    policy.serviceId(), policy.actionName(), policy.classification().name(),
                    json.writeValueAsString(policy.schema()));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("工具动作结构无法保存");
        }
    }
}
