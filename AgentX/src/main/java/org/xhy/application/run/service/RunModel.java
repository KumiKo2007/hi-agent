package org.xhy.application.run.service;

import org.xhy.domain.run.model.RunEntity;

/** 为一次简单运行生成最终回答的模型边界。 */
@FunctionalInterface
public interface RunModel {
    String execute(RunEntity run);
}
