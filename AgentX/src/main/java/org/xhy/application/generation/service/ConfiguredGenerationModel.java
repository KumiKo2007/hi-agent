package org.xhy.application.generation.service;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import org.xhy.domain.generation.service.GenerationModel;
import org.xhy.domain.llm.model.ModelEntity;
import org.xhy.domain.llm.model.ProviderEntity;
import org.xhy.infrastructure.llm.LLMServiceFactory;
/** 复用已有模型客户端，仅做无工具文本调用。 */
@Component
public class ConfiguredGenerationModel implements GenerationModel {
    private final LLMServiceFactory factory;
    public ConfiguredGenerationModel(LLMServiceFactory factory) {
        this.factory = factory;
    }
    @Override
    public String generate(ModelEntity model, ProviderEntity provider, String system, String input) {
        return factory.getStrandClient(provider, model).chat(List.of(new SystemMessage(system), new UserMessage(input)))
                .aiMessage().text();
    }
}
