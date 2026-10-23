package org.xhy.application.teaching.service;

import java.time.Instant;
import java.util.*;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.stereotype.Service;
import org.xhy.domain.teaching.model.TeachingOcr;
import org.xhy.domain.teaching.repository.TeachingOcrStore;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.rag.utils.PdfToBase64Converter;

/** 逐页显示识别覆盖，原页通过所有者校验后渲染，不暴露存储直链。 */
@Service
public class TeachingOcrAppService {
    private final TeachingOcrStore store;
    private final FileStorageService storage;
    public TeachingOcrAppService(TeachingOcrStore store, FileStorageService storage) {
        this.store = store;
        this.storage = storage;
    }
    /** 获取固定版本的每一页；缺少文本和缺少行都保持显式缺口。 */
    public TeachingOcr.File get(String versionId, String fileId, String userId) {
        return view(owned(versionId, fileId, userId));
    }
    /** 上传后尚未就绪也能检查失败页。 */
    public TeachingOcr.File current(String fileId, String userId) {
        return view(owned(null, fileId, userId));
    }
    private TeachingOcr.StoredFile owned(String versionId, String fileId, String userId) {
        var file = versionId == null
                ? store.findOwnedCurrentFile(fileId, userId)
                : store.findOwnedVersionFile(versionId, fileId, userId);
        if (file == null)
            throw new BusinessException("PDF资料不存在、版本未就绪或无访问权限");
        if (file.pageCount() != null && (file.pageCount() < 0 || file.pageCount() > 2000))
            throw new BusinessException("PDF页数超出核对范围，请拆分文件");
        return file;
    }
    private TeachingOcr.File view(TeachingOcr.StoredFile file) {
        var pages = new ArrayList<TeachingOcr.Page>();
        int total = file.pageCount() == null ? 0 : file.pageCount();
        for (int page = 1; page <= total; page++) {
            final int p = page;
            var found = file.pages().stream().filter(row -> row.physicalPage() == p).toList();
            if (found.size() != 1) {
                pages.add(new TeachingOcr.Page(null, page, null,
                        List.of(found.isEmpty() ? "此页缺少识别结果，请等待处理或替换资料/缩小范围" : "此页存在重复识别结果，请重新处理"), "MISSING", null));
                continue;
            }
            var source = found.get(0);
            String status = source.content() == null || source.content().isBlank()
                    ? "MISSING"
                    : TeachingOcr.hasHardGap(source.coverageWarnings())
                            ? "FORMULA_GAP"
                            : source.review() != null ? "HUMAN_REVIEWED" : "REVIEW_REQUIRED";
            pages.add(new TeachingOcr.Page(source.sourceId(), page, source.content(), source.coverageWarnings(), status,
                    source.review()));
        }
        return new TeachingOcr.File(file.versionId(), file.fileId(), file.fileName(), total, file.processingStatus(),
                file.processingError(), pages);
    }
    /** 记录人工核对，不修改OCR文字或原始警告；空页不能用确认绕过。 */
    public TeachingOcr.Page review(String versionId, String fileId, int physicalPage, String note, String userId) {
        var file = get(versionId, fileId, userId);
        var page = file.pages().stream().filter(p -> p.physicalPage() == physicalPage).findFirst()
                .orElseThrow(() -> new BusinessException("文件物理页超出范围"));
        if ("MISSING".equals(page.status()) || "FORMULA_GAP".equals(page.status()))
            throw new BusinessException("失败/空白页不能确认为完整，请替换资料或排除此页");
        if (note == null || note.isBlank() || note.length() > 2000)
            throw new BusinessException("请记录不超过2000字的原页核对依据");
        store.saveReview(versionId, page.sourceId(), userId,
                new TeachingOcr.Review(note.trim(), Instant.now().toString(),
                        TeachingOcr.fingerprint(page.sourceId(), page.content(), page.coverageWarnings())));
        return get(versionId, fileId, userId).pages().stream().filter(p -> p.physicalPage() == physicalPage).findFirst()
                .orElseThrow();
    }
    /** 返回受保护原页PNG，处理错误不回退到别的文件或别的页。 */
    public String image(String versionId, String fileId, int page, String userId) {
        var file = owned(versionId, fileId, userId);
        if (page < 1 || file.pageCount() == null || page > file.pageCount())
            throw new BusinessException("文件物理页超出范围");
        try {
            byte[] data = storage.download(file.storageUrl()).bytes();
            return "data:image/png;base64," + PdfToBase64Converter.processPdfPageToBase64(data, page - 1, "png");
        } catch (Exception e) {
            throw new BusinessException("原PDF页面暂不可用，请替换资料或联系管理员核对存储");
        }
    }
}
