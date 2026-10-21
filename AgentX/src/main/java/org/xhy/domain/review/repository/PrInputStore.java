package org.xhy.domain.review.repository;

import java.util.Optional;
import org.xhy.domain.review.model.PrInputSnapshot;

/** 按所有者持久化不可变 PR 输入。 */
public interface PrInputStore {
    void save(String ownerId, PrInputSnapshot input);
    Optional<PrInputSnapshot> find(String inputId, String ownerId);
}
