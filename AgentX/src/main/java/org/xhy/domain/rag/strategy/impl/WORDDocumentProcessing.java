package org.xhy.domain.rag.strategy.impl;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.dromara.x.file.storage.core.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhy.domain.rag.message.RagDocMessage;
import org.xhy.domain.rag.model.DocumentUnitEntity;
import org.xhy.domain.rag.model.FileDetailEntity;
import org.xhy.domain.rag.repository.DocumentUnitRepository;
import org.xhy.domain.rag.repository.FileDetailRepository;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;

/** Word 正文按原文段落导入；复杂内容的覆盖不足随来源保存，不猜测排版页码。 */
@Service("word")
public class WORDDocumentProcessing extends AbstractDocumentProcessingStrategy {
    private static final Logger log = LoggerFactory.getLogger(WORDDocumentProcessing.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DocumentUnitRepository documents;
    private final FileDetailRepository files;
    private final FileStorageService storage;

    public WORDDocumentProcessing(DocumentUnitRepository documents, FileDetailRepository files,
            FileStorageService storage) {
        this.documents = documents;
        this.files = files;
        this.storage = storage;
    }

    @Override
    public void handle(RagDocMessage message, String strategy) throws Exception {
        byte[] bytes = getFileData(message, strategy);
        // 解析及元数据均局限于本次调用，Spring 单例不保存当前文件ID或解析结果。
        List<Paragraph> paragraphs = parse(bytes, strategy);
        updateSize(message, paragraphs.size());
        for (int i = 0; i < paragraphs.size(); i++) {
            Paragraph paragraph = paragraphs.get(i);
            DocumentUnitEntity unit = unit(message.getFileId(), i, paragraph.text());
            unit.setSourceLocatorType(paragraph.type());
            unit.setSourceLocator(
                    JSON.writeValueAsString(Map.of("paragraphIndex", i + 1, "heading", paragraph.heading())));
            unit.setCoverageWarnings(JSON.writeValueAsString(paragraph.warnings()));
            documents.checkInsert(unit);
        }
        log.info("Word 正文处理完成：文件 {}，{} 个原文段落", message.getFileId(), paragraphs.size());
    }

    @Override
    public void pushPageSize(byte[] bytes, RagDocMessage message) {
        updateSize(message, parse(bytes, "DOCX").size());
    }

    private void updateSize(RagDocMessage message, int count) {
        message.setPageSize(count);
        files.checkedUpdate(Wrappers.<FileDetailEntity>lambdaUpdate().eq(FileDetailEntity::getId, message.getFileId())
                .set(FileDetailEntity::getFilePageSize, count));
    }

    @Override
    public byte[] getFileData(RagDocMessage message, String strategy) {
        FileDetailEntity file = files.selectById(message.getFileId());
        if (file == null)
            throw new IllegalArgumentException("Word 文件不存在，无法处理");
        return storage.download(file.getUrl()).bytes();
    }

    @Override
    public Map<Integer, String> processFile(byte[] bytes, int totalPages) {
        var result = new LinkedHashMap<Integer, String>();
        var paragraphs = parse(bytes, "DOCX");
        for (int i = 0; i < paragraphs.size(); i++)
            result.put(i, paragraphs.get(i).text());
        return result;
    }

    @Override
    public void insertData(RagDocMessage message, Map<Integer, String> data) throws Exception {
        // 兼容旧调用者的裸文本输入，绝不把无元数据的分段冒充原文段落。
        for (var entry : data.entrySet()) {
            var unit = unit(message.getFileId(), entry.getKey(), entry.getValue());
            unit.setSourceLocatorType("DOCUMENT_UNIT");
            unit.setCoverageWarnings(JSON.writeValueAsString(List.of("来源缺少原文段落定位，请重新处理 Word 文件")));
            documents.checkInsert(unit);
        }
    }

    private DocumentUnitEntity unit(String fileId, int index, String text) {
        var unit = new DocumentUnitEntity();
        unit.setFileId(fileId);
        unit.setPage(index);
        unit.setContent(text);
        unit.setIsVector(text != null && text.isBlank());
        unit.setIsOcr(text != null);
        return unit;
    }

    private List<Paragraph> parse(byte[] bytes, String extension) {
        if (bytes == null || bytes.length == 0)
            throw new IllegalArgumentException("Word 文件为空，请重新上传");
        try {
            if (org.apache.poi.poifs.filesystem.FileMagic
                    .valueOf(bytes) == org.apache.poi.poifs.filesystem.FileMagic.OLE2
                    && "DOC".equalsIgnoreCase(extension))
                return parseDoc(bytes);
            if (!List.of("DOC", "DOCX").contains(extension.toUpperCase(java.util.Locale.ROOT))) {
                String text = new ApachePoiDocumentParser().parse(new ByteArrayInputStream(bytes)).text();
                return List.of(new Paragraph(text, "", "DOCUMENT_UNIT",
                        List.of("此 Office 格式只导入文本，未取得可靠原文段落定位，请核对或转换为 Word/PDF")));
            }
            try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                var warnings = new ArrayList<String>();
                var root = document.getDocument().getDomNode();
                if (contains(root, "http://schemas.openxmlformats.org/officeDocument/2006/math", "oMath", "oMathPara"))
                    warnings.add("Word 含公式，当前仅导入可读取正文，公式结构未覆盖，请对照原文件核查");
                if (contains(root, "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "drawing", "pict",
                        "object"))
                    warnings.add("Word 含嵌图、绘图或嵌入对象，当前未覆盖其内容，请对照原文件核查");
                if (contains(root, "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "sdt", "altChunk",
                        "txbxContent", "ins", "del")) {
                    warnings.add("Word 含内容控件、外部内容、文本框或修订，当前未保证完整覆盖，请核对原文件");
                }
                if (!document.getTables().isEmpty())
                    warnings.add("Word 含表格，当前原文段落范围只包含正文，表格内容未覆盖");
                if (!document.getHeaderList().isEmpty() || !document.getFooterList().isEmpty()
                        || !document.getFootnotes().isEmpty() || !document.getEndnotes().isEmpty())
                    warnings.add("Word 页眉页脚或脚注尾注未纳入正文段落，请核对原文件");
                var result = new ArrayList<Paragraph>();
                String heading = "";
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    String text = paragraph.getText();
                    String style = paragraph.getStyle();
                    String styleName = style;
                    if (style != null && document.getStyles() != null && document.getStyles().getStyle(style) != null) {
                        styleName = document.getStyles().getStyle(style).getName();
                    }
                    boolean outlined = paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetOutlineLvl();
                    if (outlined || (styleName != null && styleName.matches("(?i)(heading|标题)\\s*\\d*")))
                        heading = text;
                    result.add(new Paragraph(text, heading, "WORD_PARAGRAPH", List.copyOf(warnings)));
                }
                if (result.isEmpty() || result.stream().allMatch(p -> p.text().isBlank()))
                    throw new IllegalArgumentException("Word 没有可定位正文段落；表格、图片或扫描内容未覆盖，请转换为可核查 PDF");
                return result;
            }
        } catch (Exception error) {
            throw new IllegalArgumentException("Word 解析失败，请检查文件是否损坏或受密码保护，并重新上传；未生成可靠来源", error);
        }
    }

    private boolean contains(org.w3c.dom.Node root, String namespace, String... names) {
        var expected = java.util.Set.of(names);
        var pending = new java.util.ArrayDeque<org.w3c.dom.Node>();
        pending.add(root);
        while (!pending.isEmpty()) {
            var node = pending.removeFirst();
            if (namespace.equals(node.getNamespaceURI()) && expected.contains(node.getLocalName()))
                return true;
            for (var child = node.getFirstChild(); child != null; child = child.getNextSibling())
                pending.add(child);
        }
        return false;
    }

    private List<Paragraph> parseDoc(byte[] bytes) throws Exception {
        try (var document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            var result = new ArrayList<Paragraph>();
            var range = document.getRange();
            var warnings = List.of("旧版 DOC 仅导入正文文本；公式、嵌图、表格及复杂格式无法保证覆盖，建议另存为 DOCX 后重新处理");
            for (int i = 0; i < range.numParagraphs(); i++) {
                result.add(
                        new Paragraph(range.getParagraph(i).text().replace("\r", ""), "", "WORD_PARAGRAPH", warnings));
            }
            if (result.isEmpty())
                throw new IllegalArgumentException("DOC 缺少可定位正文段落");
            return result;
        }
    }

    private record Paragraph(String text, String heading, String type, List<String> warnings) {
    }
}
