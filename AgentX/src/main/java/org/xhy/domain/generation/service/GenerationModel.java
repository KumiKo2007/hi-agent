package org.xhy.domain.generation.service;
import org.xhy.domain.llm.model.ModelEntity;
import org.xhy.domain.llm.model.ProviderEntity;
/** 仅外部文本模型传输可替换；授权、固定连接与输出校验均属于应用。 */
public interface GenerationModel {
    String generate(ModelEntity model, ProviderEntity provider, String system, String input);
}
