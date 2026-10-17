package org.xhy.application.run.service;

import org.junit.jupiter.api.Test;
import org.xhy.infrastructure.exception.BusinessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunEvidenceCitationAppenderTest {
    @Test
    void shouldAppendPinnedSourcesToTheApprovedBodyWithoutChangingOtherArguments() {
        String arguments = "{\"owner\":\"octo\",\"repo\":\"project\",\"title\":\"Fix setup\","
                + "\"body\":\"Document the setup steps.\"}";
        KnowledgeCitation citation = citation("setup guide.md", 4);

        String result = RunEvidenceCitationAppender.appendToBody(arguments, List.of(citation));

        assertTrue(result.contains("\"title\":\"Fix setup\""));
        assertTrue(result.contains("\"body\":\"Document the setup steps.\\n\\n---\\n\\n### Sources\\n- [S1] `setup guide.md`, page 4\""));
        assertEquals("octo/project", org.xhy.application.tool.service.McpSchema.targetRepository(result));
    }

    @Test
    void shouldRejectWritesWithoutEvidenceOrAStringBody() {
        KnowledgeCitation citation = citation("source.md", null);

        assertThrows(BusinessException.class,
                () -> RunEvidenceCitationAppender.appendToBody("{\"body\":\"draft\"}", List.of()));
        assertThrows(BusinessException.class,
                () -> RunEvidenceCitationAppender.appendToBody("{\"title\":\"draft\"}", List.of(citation)));
    }

    private static KnowledgeCitation citation(String fileName, Integer page) {
        KnowledgeCitation citation = new KnowledgeCitation();
        citation.setFileName(fileName);
        citation.setPage(page);
        citation.setDocumentId("doc-1");
        citation.setKnowledgeVersionId("version-1");
        citation.setContent("Reference text");
        return citation;
    }
}
