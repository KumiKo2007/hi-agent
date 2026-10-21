package org.xhy.infrastructure.repository.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.xhy.domain.review.model.PrInputSnapshot;
import org.xhy.domain.review.repository.PrInputStore;
import org.xhy.infrastructure.exception.BusinessException;
import java.util.Optional;

/** 快照只追加，不随 PR 或 Agent 后续变化更新。 */
@Repository
public class JdbcPrInputStore implements PrInputStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcPrInputStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void save(String ownerId, PrInputSnapshot input) {
        try {
            jdbc.update("INSERT INTO pr_input_snapshots (id, user_id, snapshot_json) VALUES (?, ?, ?)", input.id(),
                    ownerId, json.writeValueAsString(input));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("PR 输入快照保存失败");
        }
    }

    @Override
    public Optional<PrInputSnapshot> find(String inputId, String ownerId) {
        return jdbc.query("SELECT snapshot_json FROM pr_input_snapshots WHERE id = ? AND user_id = ?", (row, index) -> {
            try {
                return json.readValue(row.getString("snapshot_json"), PrInputSnapshot.class);
            } catch (JsonProcessingException exception) {
                throw new BusinessException("PR 输入快照损坏，请重新读取");
            }
        }, inputId, ownerId).stream().findFirst();
    }
}
