package org.xhy.infrastructure.repository.tool;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcAgentToolActionSelectionStoreTest {
    @Test
    void shouldPreservePreauthorizationsForActionsThatRemainSelected() {
        InMemoryJdbcTemplate jdbc = new InMemoryJdbcTemplate();
        jdbc.selectedActions.addAll(List.of("create_issue", "close_issue"));
        jdbc.preauthorizedActions.addAll(List.of("create_issue", "close_issue"));
        JdbcAgentToolActionSelectionStore store = new JdbcAgentToolActionSelectionStore(jdbc);

        store.replace("agent-1", "service-1", List.of("create_issue", "comment_issue"));

        assertEquals(Set.of("create_issue", "comment_issue"), jdbc.selectedActions);
        assertEquals(Set.of("create_issue"), jdbc.preauthorizedActions);
        assertEquals(1, jdbc.removedSelections);
        assertEquals(1, jdbc.addedSelections);
    }

    private static final class InMemoryJdbcTemplate extends JdbcTemplate {
        private final Set<String> selectedActions = new LinkedHashSet<>();
        private final Set<String> preauthorizedActions = new LinkedHashSet<>();
        private int removedSelections;
        private int addedSelections;

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper, Object... args) {
            if (!sql.startsWith("SELECT action_name FROM agent_tool_actions")) {
                throw new UnsupportedOperationException(sql);
            }
            return (List<T>) new ArrayList<>(selectedActions);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.equals("DELETE FROM agent_tool_actions WHERE agent_id = ? AND service_id = ?")) {
                selectedActions.clear();
                preauthorizedActions.clear();
                removedSelections++;
                return 1;
            }
            if (sql.startsWith(
                    "DELETE FROM agent_tool_actions WHERE agent_id = ? AND service_id = ? AND action_name")) {
                String actionName = (String) args[2];
                selectedActions.remove(actionName);
                preauthorizedActions.remove(actionName);
                removedSelections++;
                return 1;
            }
            if (sql.startsWith("INSERT INTO agent_tool_actions")) {
                selectedActions.add((String) args[2]);
                addedSelections++;
                return 1;
            }
            throw new UnsupportedOperationException(sql);
        }
    }
}
