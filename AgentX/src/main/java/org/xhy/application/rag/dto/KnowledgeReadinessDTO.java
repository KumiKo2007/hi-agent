package org.xhy.application.rag.dto;

/** 数据集当前处理状态与最近一个不可变的可检索版本。 */
public class KnowledgeReadinessDTO {

    private String datasetId;
    private String status;
    private int fileCount;
    private int readyFileCount;
    private int failedFileCount;
    private String failureReason;
    private String latestReadyVersionId;
    private String latestReadyVersionNumber;

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public int getReadyFileCount() {
        return readyFileCount;
    }

    public void setReadyFileCount(int readyFileCount) {
        this.readyFileCount = readyFileCount;
    }

    public int getFailedFileCount() {
        return failedFileCount;
    }

    public void setFailedFileCount(int failedFileCount) {
        this.failedFileCount = failedFileCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getLatestReadyVersionId() {
        return latestReadyVersionId;
    }

    public void setLatestReadyVersionId(String latestReadyVersionId) {
        this.latestReadyVersionId = latestReadyVersionId;
    }

    public String getLatestReadyVersionNumber() {
        return latestReadyVersionNumber;
    }

    public void setLatestReadyVersionNumber(String latestReadyVersionNumber) {
        this.latestReadyVersionNumber = latestReadyVersionNumber;
    }
}
