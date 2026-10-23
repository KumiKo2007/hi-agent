package org.xhy.domain.teaching.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** 识别结果与人工核对分开记录，核对不能改变原版本。 */
public final class TeachingOcr {
    private TeachingOcr() {
    }
    public record Review(String note, String reviewedAt, String contentHash) {
    }
    public record Page(String sourceId, int physicalPage, String content, List<String> coverageWarnings, String status,
            Review review) {
    }
    public record File(String versionId, String fileId, String fileName, int pageCount, Integer processingStatus,
            String processingError, List<Page> pages) {
    }
    public record StoredFile(String versionId, String fileId, String fileName, Integer pageCount, String storageUrl,
            Integer processingStatus, String processingError, List<Page> pages) {
    }

    /** 格式残缺表示输入尚未补全，人工确认不能补出缺失公式。 */
    public static boolean hasHardGap(List<String> warnings) {
        return warnings.stream().anyMatch(w -> w.startsWith("OCR_FORMULA_GAP:") || w.startsWith("OCR_METADATA_GAP:"));
    }

    /** 核对凭据只对确切的来源内容和覆盖警告有效。 */
    public static String fingerprint(String sourceId, String content, List<String> warnings) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(sourceId, content == null ? "" : content, String.join("\n", warnings))) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
