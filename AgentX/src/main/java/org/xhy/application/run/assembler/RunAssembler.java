package org.xhy.application.run.assembler;

import org.springframework.beans.BeanUtils;
import org.xhy.application.run.dto.RunDTO;
import org.xhy.application.run.dto.RunStepDTO;
import org.xhy.domain.run.model.RunEntity;
import org.xhy.domain.run.model.RunStepEntity;

import java.util.List;

/** 将持久运行转换为使用者可见的数据。 */
public final class RunAssembler {
    private RunAssembler() {
    }

    public static RunDTO toDTO(RunEntity run, List<RunStepEntity> steps) {
        if (run == null) {
            return null;
        }
        RunDTO dto = new RunDTO();
        BeanUtils.copyProperties(run, dto);
        dto.setSteps(steps.stream().map(RunAssembler::toDTO).toList());
        return dto;
    }

    public static RunStepDTO toDTO(RunStepEntity step) {
        if (step == null) {
            return null;
        }
        RunStepDTO dto = new RunStepDTO();
        BeanUtils.copyProperties(step, dto);
        return dto;
    }
}
