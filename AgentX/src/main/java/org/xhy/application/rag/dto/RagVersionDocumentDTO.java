package org.xhy.application.rag.dto;

import java.time.LocalDateTime;

/** RAG版本文档DTO
 * @author xhy
 * @date 2025-07-18 <br/>
 */
public class RagVersionDocumentDTO {

    /** 文档ID */
    private String id;

    /** 文档内容 */
    private String content;

    /** 页码 */
    private Integer page;

    /** 关联的文件名 */
    private String fileName;

    /** 固定版本中的文件快照 ID。 */
    private String ragVersionFileId;

    /** 原始文档单元 ID，仅用于追溯，不用于检索。 */
    private String originalDocumentId;

    /** 可打开的固定版本来源地址。 */
    private String sourceUrl;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 来源位置类型；历史数据为空时不得猜测原文位置。 */
    private String sourceLocatorType;
    /** 原文定位 JSON，Word 包含从 1 开始的 paragraphIndex 和 heading。 */
    private String sourceLocator;
    /** 导入覆盖不足的中文提示 JSON 数组。 */
    private String coverageWarnings;

    public String getSourceLocatorType() {
        return sourceLocatorType;
    }
    public void setSourceLocatorType(String value) {
        sourceLocatorType = value;
    }
    public String getSourceLocator() {
        return sourceLocator;
    }
    public void setSourceLocator(String value) {
        sourceLocator = value;
    }
    public String getCoverageWarnings() {
        return coverageWarnings;
    }
    public void setCoverageWarnings(String value) {
        coverageWarnings = value;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getRagVersionFileId() {
        return ragVersionFileId;
    }

    public void setRagVersionFileId(String ragVersionFileId) {
        this.ragVersionFileId = ragVersionFileId;
    }

    public String getOriginalDocumentId() {
        return originalDocumentId;
    }

    public void setOriginalDocumentId(String originalDocumentId) {
        this.originalDocumentId = originalDocumentId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
