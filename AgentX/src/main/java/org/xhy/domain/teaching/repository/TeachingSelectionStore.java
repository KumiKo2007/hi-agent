package org.xhy.domain.teaching.repository;

import java.util.List;
import org.xhy.domain.teaching.model.TeachingSelection;

/** 选材及已就绪知识快照的所有者隔离存储。 */
public interface TeachingSelectionStore {
    TeachingSelection.Version findOwnedVersion(String datasetId, String versionId, String userId);
    void save(TeachingSelection selection, String userId);
    TeachingSelection findOwned(String id, String userId);
    List<TeachingSelection> listOwned(String userId);
}
