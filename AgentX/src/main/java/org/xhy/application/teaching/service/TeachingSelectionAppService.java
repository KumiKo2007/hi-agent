package org.xhy.application.teaching.service;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xhy.domain.teaching.model.TeachingSelection;
import org.xhy.domain.teaching.repository.TeachingSelectionStore;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.application.teaching.request.SaveTeachingSelectionRequest;

/** 只固定教师明确选择的范围，并披露缺页和来源不足。 */
@Service
public class TeachingSelectionAppService {
    private final TeachingSelectionStore store;
    public TeachingSelectionAppService(TeachingSelectionStore store) {
        this.store = store;
    }

    /** 从拥有的就绪快照保存新的不可变选材。 */
    @Transactional(transactionManager = "transactionManager")
    public TeachingSelection save(SaveTeachingSelectionRequest request, String userId) {
        var version = store.findOwnedVersion(request.datasetId(), request.versionId(), userId);
        if (version == null) {
            throw new BusinessException("知识版本尚未就绪或无访问权限，请完成资料处理并确认可检索版本");
        }
        String id = UUID.randomUUID().toString();
        var ranges = new ArrayList<TeachingSelection.Range>();
        var selected = new LinkedHashMap<String, TeachingSelection.Source>();
        var warnings = new ArrayList<String>();
        boolean incomplete = false;
        long pageCount = 0;
        for (var range : request.ranges()) {
            var file = version.files().stream().filter(f -> f.id().equals(range.fileId())).findFirst()
                    .orElseThrow(() -> new BusinessException("所选文件不属于该固定知识版本"));
            if (range.endPage() < range.startPage()
                    || (file.pageCount() != null && range.endPage() > file.pageCount())) {
                throw new BusinessException("请填写来源范围，结束位置不得小于开始位置或超出文件来源数量");
            }
            pageCount += (long) range.endPage() - range.startPage() + 1;
            if (pageCount > 500) {
                throw new BusinessException("一次选材最多 500 个来源位置，请缩小章节范围");
            }
            if (!("pdf".equalsIgnoreCase(file.fileType()) || "application/pdf".equalsIgnoreCase(file.fileType())
                    || "docx".equalsIgnoreCase(file.fileType()) || "doc".equalsIgnoreCase(file.fileType()))) {
                incomplete = true;
                warnings.add(file.fileName() + " 目前只有处理单元定位，不能作为文件物理页引用，请核对段落来源或使用 PDF");
            }
            ranges.add(
                    new TeachingSelection.Range(range.fileId(), range.startPage(), range.endPage(), range.chapter()));
            for (long page = range.startPage(); page <= range.endPage(); page++) {
                final int physicalPage = (int) page;
                var rows = version.sources().stream()
                        .filter(s -> s.fileId().equals(range.fileId()) && s.rangeIndex() == physicalPage
                                && s.content() != null
                                && (!s.content().isBlank() || "WORD_PARAGRAPH".equals(s.locatorType())))
                        .toList();
                if (rows.size() > 1 && ("pdf".equalsIgnoreCase(file.fileType())
                        || "application/pdf".equalsIgnoreCase(file.fileType()))) {
                    incomplete = true;
                    warnings.add(file.fileName() + " 文件物理第 " + page + " 页存在重复识别来源，请重新处理资料");
                }
                if (rows.isEmpty()) {
                    incomplete = true;
                    String location = ("docx".equalsIgnoreCase(file.fileType())
                            || "doc".equalsIgnoreCase(file.fileType()))
                                    ? " 原文第 " + page + " 段"
                                    : " 文件物理第 " + page + " 页";
                    warnings.add(file.fileName() + location + "缺少可用来源，请核对识别结果或缩小范围");
                }
                for (var source : rows) {
                    if (source.coverageWarnings() != null && !source.coverageWarnings().isEmpty()) {
                        warnings.addAll(source.coverageWarnings());
                        if (!"FILE_PAGE".equals(source.locatorType()) || source.review() == null
                                || org.xhy.domain.teaching.model.TeachingOcr.hasHardGap(source.coverageWarnings()))
                            incomplete = true;
                    }
                    selected.put(source.id(),
                            new TeachingSelection.Source(source.id(), source.fileId(), source.fileName(),
                                    source.physicalPage(), source.locatorType(), source.content(),
                                    "/teaching/selections/" + id + "/sources/" + source.id(), source.paragraphIndex(),
                                    source.heading(), source.coverageWarnings(), source.review()));
                }
            }
        }
        if (selected.values().stream().noneMatch(source -> source.content() != null && !source.content().isBlank())) {
            incomplete = true;
            warnings.add("所选范围未包含可用正文，请核对原文并扩大范围");
        }
        var selection = new TeachingSelection(id, request.title().trim(), request.datasetId(), version.id(),
                version.versionNumber(), List.copyOf(ranges), List.copyOf(selected.values()),
                incomplete ? "INCOMPLETE" : "READY", warnings.stream().distinct().toList(), Instant.now().toString());
        store.save(selection, userId);
        return selection;
    }

    /** 只读取当前所有者保存的固定内容。 */
    public TeachingSelection get(String id, String userId) {
        var selection = store.findOwned(id, userId);
        if (selection == null) {
            throw new BusinessException("选材不存在或无访问权限");
        }
        return selection;
    }

    /** 返回当前所有者的选材历史。 */
    public List<TeachingSelection> list(String userId) {
        return store.listOwned(userId);
    }

    /** 来源打开也必须经过选材所有者权限和实际选择范围验证。 */
    public TeachingSelection.Source source(String id, String sourceId, String userId) {
        return get(id, userId).sources().stream().filter(s -> s.id().equals(sourceId)).findFirst()
                .orElseThrow(() -> new BusinessException("来源不在本次选材范围内"));
    }
}
