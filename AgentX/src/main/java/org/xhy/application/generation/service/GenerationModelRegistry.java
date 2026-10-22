package org.xhy.application.generation.service;
import org.springframework.stereotype.Service;
import org.xhy.domain.llm.model.ModelEntity;
import org.xhy.domain.llm.model.ProviderEntity;
import org.xhy.domain.llm.model.enums.ModelType;
import org.xhy.domain.llm.service.LLMDomainService;
import org.xhy.infrastructure.exception.BusinessException;
import java.util.Objects;
/** 在真正模型注册表上校验访问、启用状态与连接，不能由模型替身绕过。 */
@Service
public class GenerationModelRegistry {
    public record Access(ModelEntity model, ProviderEntity provider) {
        public String connectionType() {
            return provider.getProtocol().name();
        }
    }
    private final LLMDomainService llms;
    public GenerationModelRegistry(LLMDomainService llms) {
        this.llms = llms;
    }
    public Access resolve(String modelId, String userId) {
        if (modelId == null || modelId.isBlank())
            throw new BusinessException("请先在模型设置中配置可用模型");
        var model = llms.getModelById(modelId);
        if (!Boolean.TRUE.equals(model.getStatus()) || model.getType() != ModelType.CHAT)
            throw new BusinessException("请选择已启用的对话模型");
        if (!Boolean.TRUE.equals(model.getOfficial()) && !Objects.equals(userId, model.getUserId()))
            throw new BusinessException("模型不存在或无访问权限");
        var provider = llms.getProvider(model.getProviderId());
        if (!Boolean.TRUE.equals(provider.getStatus()) || provider.getProtocol() == null)
            throw new BusinessException("模型服务商未激活或缺少连接类型");
        provider.isAvailable(userId);
        if (provider.getConfig() == null || provider.getConfig().getApiKey() == null
                || provider.getConfig().getApiKey().isBlank())
            throw new BusinessException("模型尚未配置连接凭据，请在模型设置中完成配置");
        return new Access(model, provider);
    }
}
