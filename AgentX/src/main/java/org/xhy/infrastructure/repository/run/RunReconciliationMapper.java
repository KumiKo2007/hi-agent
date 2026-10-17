package org.xhy.infrastructure.repository.run;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.xhy.domain.run.model.RunReconciliationEntity;

@Mapper
public interface RunReconciliationMapper extends BaseMapper<RunReconciliationEntity> {
}
