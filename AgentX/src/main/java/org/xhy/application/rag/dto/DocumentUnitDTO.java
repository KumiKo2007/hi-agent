package org.xhy.application.rag.dto;

/** 文档单元响应DTO
 * 
 * @author shilong.zang */
public class DocumentUnitDTO {

    /** 主键 */
    private String id;

    /** 文件ID */
    private String fileId;

    /** 页码 */
    private Integer page;

    /** 内容 */
    private String content;

    /** 是否OCR处理 */
    private Boolean isOcr;

    /** 是否向量化 */
    private Boolean isVector;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

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

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getIsOcr() {
        return isOcr;
    }

    public void setIsOcr(Boolean isOcr) {
        this.isOcr = isOcr;
    }

    public Boolean getIsVector() {
        return isVector;
    }

    public void setIsVector(Boolean isVector) {
        this.isVector = isVector;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return content;

    }
}