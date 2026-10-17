package org.xhy.application.run.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.xhy.application.run.dto.RunDTO;
import org.xhy.application.run.dto.RunApprovalDTO;
import org.xhy.application.run.dto.RunModelUsageDTO;
import org.xhy.application.run.dto.RunStepDTO;
import org.xhy.application.run.dto.RunTimelineEventDTO;
import org.xhy.application.run.service.RunNextActionResolver;
import org.xhy.application.run.service.RunTimelineSanitizer;
import org.xhy.domain.run.model.RunApprovalEntity;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunTimelineEventData;
import org.xhy.domain.run.model.RunStepEntity;
import org.xhy.domain.run.model.RunTimelineEventEntity;
import org.xhy.domain.run.model.RunTimelineEventType;

import java.util.List;

/** 将持久运行转换为使用者可见的数据。 */
public final class RunAssembler {
    private static final ObjectMapper JSON = new ObjectMapper();

    private RunAssembler() {
    }

    public static RunDTO toDTO(RunEntity run, List<RunStepEntity> steps) {
        return toDTO(run, steps, null);
    }

    public static RunDTO toDTO(RunEntity run, List<RunStepEntity> steps, RunApprovalEntity approval) {
        return toDTO(run, steps, approval, List.of());
    }

    public static RunDTO toDTO(RunEntity run, List<RunStepEntity> steps, RunApprovalEntity approval,
            List<RunTimelineEventEntity> events) {
        if (run == null) {
            return null;
        }
        RunDTO dto = new RunDTO();
        BeanUtils.copyProperties(run, dto);
        dto.setGoal(RunTimelineSanitizer.redactText(run.getGoal()));
        dto.setResult(RunTimelineSanitizer.redactText(run.getResult()));
        dto.setErrorMessage(RunTimelineSanitizer.redactText(run.getErrorMessage()));
        dto.setTargetRepository(RunTimelineSanitizer.redactText(run.getTargetRepository()));
        dto.setSteps(steps.stream().map(RunAssembler::toDTO).toList());
        if (approval != null) {
            dto.setApproval(toDTO(approval));
        }
        List<RunTimelineEventDTO> timeline = events.stream().filter(RunAssembler::isKnownEvent).map(RunAssembler::toDTO)
                .toList();
        dto.setTimeline(timeline);
        dto.setModelUsage(usage(timeline));
        dto.setNextAction(RunNextActionResolver.resolve(run.getStatus(), latestFailureCode(timeline)).name());
        return dto;
    }

    private static RunApprovalDTO toDTO(RunApprovalEntity approval) {
        RunApprovalDTO dto = new RunApprovalDTO();
        BeanUtils.copyProperties(approval, dto);
        dto.setServiceId(RunTimelineSanitizer.redactText(approval.getServiceId()));
        dto.setActionName(RunTimelineSanitizer.redactText(approval.getActionName()));
        dto.setActionDescription(RunTimelineSanitizer.redactText(approval.getActionDescription()));
        dto.setTargetRepository(RunTimelineSanitizer.redactText(approval.getTargetRepository()));
        dto.setArguments(RunTimelineSanitizer.redactJson(approval.getArguments()));
        dto.setExternalResourceId(RunTimelineSanitizer.redactText(approval.getExternalResourceId()));
        dto.setReconciliationReason(RunTimelineSanitizer.redactText(approval.getReconciliationReason()));
        dto.setReconciliationEvidence(RunTimelineSanitizer.redactText(approval.getReconciliationEvidence()));
        dto.setReconciliationEvidenceReference(
                RunTimelineSanitizer.redactText(approval.getReconciliationEvidenceReference()));
        return dto;
    }

    private static boolean isKnownEvent(RunTimelineEventEntity event) {
        try {
            RunTimelineEventType.valueOf(event.getEventType());
            return true;
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return false;
        }
    }

    private static RunTimelineEventDTO toDTO(RunTimelineEventEntity event) {
        return new RunTimelineEventDTO(event.getId(), event.getRunId(), event.getStepId(), event.getEventType(),
                event.getStatus(), RunTimelineSanitizer.redactText(event.getSummary()), safeDetails(event),
                event.getCreatedAt());
    }

    private static RunTimelineEventData safeDetails(RunTimelineEventEntity event) {
        try {
            RunTimelineEventData details = JSON.readValue(event.getDetailsJson(), RunTimelineEventData.class);
            if (details == null) {
                return RunTimelineEventData.empty();
            }
            String usageStatus = "AVAILABLE".equals(details.usageStatus())
                    ? "AVAILABLE"
                    : "UNAVAILABLE".equals(details.usageStatus()) ? "UNAVAILABLE" : null;
            return new RunTimelineEventData(safeFailureCode(details.failureCode()), safe(details.modelName()),
                    nonNegative(details.inputTokens()), nonNegative(details.outputTokens()), usageStatus,
                    nonNegative(details.durationMs()), safe(details.knowledgeVersionId()),
                    safe(details.sourceDocumentId()), safe(details.sourceFileId()), safe(details.sourceName()),
                    nonNegative(details.sourcePage()), safeSourceUrl(details.sourceUrl()), safe(details.serviceId()),
                    safe(details.actionName()), safe(details.targetRepository()), safe(details.actorId()),
                    safeDecision(details.decision()), safeExternalReference(details.resultReference()));
        } catch (Exception ignored) {
            return RunTimelineEventData.empty();
        }
    }

    private static String latestFailureCode(List<RunTimelineEventDTO> events) {
        return events.stream().filter(event -> "RUN_FAILED".equals(event.eventType())).map(RunTimelineEventDTO::details)
                .map(RunTimelineEventData::failureCode).filter(code -> code != null && !code.isBlank())
                .reduce((previous, latest) -> latest).orElse(null);
    }

    private static RunModelUsageDTO usage(List<RunTimelineEventDTO> events) {
        List<RunTimelineEventDTO> calls = events.stream().filter(event -> "MODEL_CALL".equals(event.eventType()))
                .toList();
        long input = 0;
        long output = 0;
        boolean complete = true;
        for (RunTimelineEventDTO call : calls) {
            RunTimelineEventData details = call.details();
            if (!"AVAILABLE".equals(details.usageStatus()) || details.inputTokens() == null
                    || details.outputTokens() == null) {
                complete = false;
                continue;
            }
            input += details.inputTokens();
            output += details.outputTokens();
        }
        return new RunModelUsageDTO(calls.size(), input, output, complete);
    }

    private static String safe(String value) {
        return RunTimelineSanitizer.redactText(value);
    }

    private static String safeFailureCode(String value) {
        try {
            return value == null ? null : org.xhy.domain.run.model.RunFailureCode.valueOf(value).name();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String safeDecision(String value) {
        return value != null && List.of("APPROVED", "DENIED", "PREAUTHORIZED", "VERIFIED_CREATED",
                "VERIFIED_NOT_CREATED", "STOPPED", "UNKNOWN").contains(value) ? value : null;
    }

    private static String safeSourceUrl(String value) {
        return value != null && value.startsWith("/rag/datasets/") ? safe(value) : null;
    }

    private static String safeExternalReference(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String safe = RunTimelineSanitizer.redactText(value);
        if (safe.matches("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/issues/[1-9][0-9]*")
                || safe.matches("[A-Za-z0-9][A-Za-z0-9._:#/-]{0,199}")) {
            return safe;
        }
        return null;
    }

    private static Integer nonNegative(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private static Long nonNegative(Long value) {
        return value == null || value < 0 ? null : value;
    }

    public static RunStepDTO toDTO(RunStepEntity step) {
        if (step == null) {
            return null;
        }
        RunStepDTO dto = new RunStepDTO();
        BeanUtils.copyProperties(step, dto);
        dto.setResult(RunTimelineSanitizer.redactText(step.getResult()));
        dto.setErrorMessage(RunTimelineSanitizer.redactText(step.getErrorMessage()));
        return dto;
    }
}
