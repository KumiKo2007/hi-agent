package org.xhy.domain.teaching.model;

import java.util.List;

/** 教师确定的一份不可变选材；页码明确使用从 1 开始的文件物理页。 */
public record TeachingSelection(String id, String title, String datasetId, String versionId, String versionNumber,
        List<Range> ranges, List<Source> sources, String status, List<String> warnings, String createdAt) {
    public record Range(String fileId, int startPage, int endPage, String chapter) {
    }
    public record Source(String id, String fileId, String fileName, int physicalPage, String locatorType,
            String content, String sourceUrl) {
    }
    public record Version(String id, String versionNumber, List<File> files, List<Source> sources) {
    }
    public record File(String id, String fileName, String fileType, Integer pageCount) {
    }
}
