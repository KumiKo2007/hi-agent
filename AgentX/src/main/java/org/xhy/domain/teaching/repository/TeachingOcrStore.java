package org.xhy.domain.teaching.repository;

import org.xhy.domain.teaching.model.TeachingOcr;

/** 在拥有者边界内读取每一页，核对凭据绑定固定版本。 */
public interface TeachingOcrStore {
    TeachingOcr.StoredFile findOwnedVersionFile(String versionId, String fileId, String userId);
    TeachingOcr.StoredFile findOwnedCurrentFile(String fileId, String userId);
    void saveReview(String versionId, String sourceId, String userId, TeachingOcr.Review review);
}
