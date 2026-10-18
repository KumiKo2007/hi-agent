import React, { useEffect, useState } from "react";
import { Switch } from "@/components/ui/switch";
import { Skeleton } from "@/components/ui/skeleton";
import { getInstalledTools, getMcpServiceActions, getAgentToolActions, replaceAgentToolActions,
  getAgentActionPreauthorizations, replaceAgentActionPreauthorizations } from "@/lib/tool-service";
import { getAvailableKnowledgeBasesWithToast } from "@/lib/agent-knowledge-base-service"; // 导入获取知识库的函数
import type { Tool, McpToolAction } from "@/types/tool";
import type { AgentTool } from "@/types/agent"; // <-- Import AgentTool
import type { KnowledgeBase } from "@/lib/agent-knowledge-base-service"; // 导入 KnowledgeBase 类型

// 用于全局缓存已加载的工具
let cachedTools: Tool[] | null = null;

// 用于全局缓存已加载的知识库
let cachedKnowledgeBases: KnowledgeBase[] | null = null;

interface AgentToolsFormProps {
  agentId?: string;
  formData: { // 只修改 formData 中 tools 的类型
    tools: AgentTool[]; // <-- Use AgentTool[]
    knowledgeBaseIds: string[];
  };
  selectedType?: "chat" | "agent"; // 可选参数，保持向后兼容
  toggleTool: (tool: Tool) => void; // <--- 修改签名以接受完整的 Tool 对象
  toggleKnowledgeBase: (kbId: string, kbName?: string) => void;
  onToolClick: (tool: Tool) => void;
  onKnowledgeBaseClick: (knowledgeBase: KnowledgeBase) => void;
  updateToolPresetParameters?: (toolId: string, presetParams: Record<string, Record<string, string>>) => void;
}

const AgentToolsForm: React.FC<AgentToolsFormProps> = ({
  agentId,
  formData,
  selectedType,
  toggleTool,
  toggleKnowledgeBase,
  onToolClick,
  onKnowledgeBaseClick,
  updateToolPresetParameters,
}) => {
  const [installedTools, setInstalledTools] = useState<Tool[]>(cachedTools || []);
  const [isLoadingTools, setIsLoadingTools] = useState(cachedTools ? false : true);
  const [actionsByService, setActionsByService] = useState<Record<string, McpToolAction[]>>({});
  const [selectedActions, setSelectedActions] = useState<Record<string, string[]>>({});
  const [preauthorizedActions, setPreauthorizedActions] = useState<Record<string, string[]>>({});
  const [configurableServices, setConfigurableServices] = useState<Record<string, boolean>>({});
  const [savingAction, setSavingAction] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>(cachedKnowledgeBases || []);
  const [isLoadingKnowledgeBases, setIsLoadingKnowledgeBases] = useState(cachedKnowledgeBases ? false : true);

  useEffect(() => {
    // 如果已经有缓存数据，直接使用不重新请求
    if (cachedTools) {
      setInstalledTools(cachedTools);
      setIsLoadingTools(false);
      return;
    }

    const fetchTools = async () => {
      try {
        const response = await getInstalledTools({ pageSize: 100 });
        if (response.code === 200 && response.data && Array.isArray(response.data.records)) {
          const tools = response.data.records;
          setInstalledTools(tools);
          // 缓存工具数据
          cachedTools = tools;
        } else {

          setInstalledTools([]);
        }
      } catch (error) {

        setInstalledTools([]);
      } finally {
        setIsLoadingTools(false);
      }
    };

    fetchTools();

    // 获取知识库列表
    if (cachedKnowledgeBases) {
      setKnowledgeBases(cachedKnowledgeBases);
      setIsLoadingKnowledgeBases(false);
    } else {
      const fetchKnowledgeBases = async () => {
        try {
          const response = await getAvailableKnowledgeBasesWithToast();
          if (response.code === 200 && response.data) {
            const kbs = response.data;
            setKnowledgeBases(kbs);
            cachedKnowledgeBases = kbs; // 缓存数据
          } else {

            setKnowledgeBases([]);
          }
        } catch (error) {

          setKnowledgeBases([]);
        } finally {
          setIsLoadingKnowledgeBases(false);
        }
      };
      fetchKnowledgeBases();
    }
  }, []);

  useEffect(() => {
    let active = true;
    const loadActions = async () => {
      const serviceIds = installedTools.map(tool => tool.toolId || tool.id).filter(Boolean);
      const entries = await Promise.all(serviceIds.map(async serviceId => {
        try {
          const [actions, selected, preauthorized] = await Promise.all([
            getMcpServiceActions(serviceId),
            agentId ? getAgentToolActions(agentId, serviceId) : Promise.resolve(null),
            agentId ? getAgentActionPreauthorizations(agentId, serviceId) : Promise.resolve(null),
          ]);
          return { serviceId, actions: actions.code === 200 ? actions.data : [],
            selected: selected?.code === 200 ? selected.data : [],
            preauthorized: preauthorized?.code === 200 ? preauthorized.data : [],
            configurable: selected?.code === 200 };
        } catch {
          return { serviceId, actions: [], selected: [], preauthorized: [], configurable: false };
        }
      }));
      if (!active) return;
      setActionsByService(Object.fromEntries(entries.map(entry => [entry.serviceId, entry.actions])));
      setSelectedActions(Object.fromEntries(entries.map(entry => [entry.serviceId, entry.selected])));
      setPreauthorizedActions(Object.fromEntries(entries.map(entry => [entry.serviceId, entry.preauthorized])));
      setConfigurableServices(Object.fromEntries(entries.map(entry => [entry.serviceId, entry.configurable])));
    };
    if (installedTools.length > 0) void loadActions();
    return () => { active = false; };
  }, [installedTools, agentId]);

  const toggleAction = async (serviceId: string, actionName: string) => {
    if (!agentId || savingAction) return;
    const current = selectedActions[serviceId] || [];
    const next = current.includes(actionName)
      ? current.filter(name => name !== actionName)
      : [...current, actionName];
    setSavingAction(`${serviceId}:${actionName}`);
    setActionError(null);
    try {
      const response = await replaceAgentToolActions(agentId, serviceId, next);
      if (response.code === 200) {
        setSelectedActions(previous => ({ ...previous, [serviceId]: response.data }));
        if (current.includes(actionName)) {
          setPreauthorizedActions(previous => ({
            ...previous,
            [serviceId]: (previous[serviceId] || []).filter(name => name !== actionName),
          }));
        }
      } else {
        setActionError(response.message || '工具动作保存失败');
      }
    } catch {
      setActionError('工具动作保存失败，请重试');
    } finally {
      setSavingAction(null);
    }
  };

  const togglePreauthorization = async (serviceId: string, actionName: string) => {
    if (!agentId || savingAction) return;
    const current = preauthorizedActions[serviceId] || [];
    const next = current.includes(actionName)
      ? current.filter(name => name !== actionName)
      : [...current, actionName];
    setSavingAction(`${serviceId}:preauthorization:${actionName}`);
    setActionError(null);
    try {
      const response = await replaceAgentActionPreauthorizations(agentId, serviceId, next);
      if (response.code === 200) {
        setPreauthorizedActions(previous => ({ ...previous, [serviceId]: response.data }));
      } else {
        setActionError(response.message || '预授权保存失败');
      }
    } catch {
      setActionError('预授权保存失败，请重试');
    } finally {
      setSavingAction(null);
    }
  };

  // 检查工具是否被选中
  const isToolSelected = (tool: Tool): boolean => {
    // 确保使用 toolId 作为工具的唯一标识符进行匹配
    if (!tool.toolId) {

      return false;
    }
    return formData.tools.some(selectedTool => selectedTool.id === tool.toolId);
  };

  return (
    <div className="space-y-6">
      {/* 工具选择 */}
      <div>
        <h2 className="text-lg font-medium mb-2">MCP 服务与工具动作</h2>
        <p className="text-sm text-muted-foreground mb-2">
          先选择 MCP 服务，再逐项启用工具动作。未分类动作按需要审批的写入处理。
        </p>
        <div className="min-h-[200px]">
          {isLoadingTools ? (
            <div className="grid grid-cols-2 gap-4 mt-4">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-14 w-full rounded-lg" />
              ))}
            </div>
          ) : installedTools.length > 0 ? (
            <div className="grid grid-cols-2 gap-4 mt-4">
              {installedTools.map((tool) => {
                const isSelected = isToolSelected(tool);

                return (
                  <div
                    key={tool.id}
                    className={`border rounded-lg p-4 cursor-pointer transition-all ${
                      isSelected ? "border-primary bg-primary/10" : "hover:border-border"
                    }`}
                    onClick={() => onToolClick(tool)}
                  >
                    <div className="flex items-center justify-between">
                      <h3 className="font-medium">{tool.name}</h3>
                      <Switch
                        checked={isSelected}
                        onCheckedChange={() => toggleTool(tool)}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground" role="status">
                      {tool.transportType === "STREAMABLE_HTTP"
                        ? "Streamable HTTP · 当前"
                        : tool.transportType === "LEGACY_SSE"
                          ? "Legacy SSE · 迁移期"
                          : "MCP 传输状态未知"}
                    </p>
                    <div className="mt-3 space-y-2" onClick={event => event.stopPropagation()}>
                      {(actionsByService[tool.toolId || tool.id] || []).map(action => {
                        const serviceId = tool.toolId || tool.id;
                        const enabled = (selectedActions[serviceId] || []).includes(action.name);
                        const preauthorized = (preauthorizedActions[serviceId] || []).includes(action.name);
                        const canChange = Boolean(agentId && isSelected && configurableServices[serviceId]);
                        const canPreauthorize = canChange && enabled && action.explicitlyClassified
                          && action.classification === 'WRITE_REQUIRES_APPROVAL';
                        return (
                          <div key={action.name} className="flex items-center justify-between gap-3 rounded-md border p-2">
                            <div className="min-w-0">
                              <div className="text-sm font-medium break-all">{action.name}</div>
                              <div className="text-xs text-muted-foreground">
                                {action.classification === 'READ_ONLY' ? '只读动作' :
                                  action.explicitlyClassified ? '已分类的外部写入 · 默认需审批' : '未分类的外部写入 · 必须逐次审批'}
                              </div>
                            </div>
                            <div className="flex flex-col items-end gap-2">
                              <Switch
                                aria-label={`允许 ${action.name}`}
                                checked={enabled}
                                disabled={!canChange || savingAction !== null}
                                onCheckedChange={() => void toggleAction(serviceId, action.name)}
                              />
                              {action.classification !== 'READ_ONLY' && (
                                <label className="flex items-center gap-2 text-xs text-muted-foreground">
                                  创建者预授权
                                  <Switch
                                    aria-label={`创建者预授权 ${action.name}`}
                                    checked={preauthorized}
                                    disabled={!canPreauthorize || savingAction !== null}
                                    onCheckedChange={() => void togglePreauthorization(serviceId, action.name)}
                                  />
                                </label>
                              )}
                            </div>
                          </div>
                        );
                      })}
                      {!agentId && <p className="text-xs text-muted-foreground">保存 Agent 后可逐项启用动作。</p>}
                      {agentId && isSelected && !configurableServices[tool.toolId || tool.id] && (
                        <p className="text-xs text-muted-foreground">先保存 MCP 服务选择，再启用动作。</p>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground mt-4">没有找到已安装的工具。</p>
          )}
          {actionError && <p role="alert" className="mt-2 text-sm text-destructive">{actionError}</p>}
        </div>
      </div>

      {/* 知识库选择 */}
      <div>
        <h2 className="text-lg font-medium mb-2">知识库</h2>
        <p className="text-sm text-muted-foreground mb-2">选择助理可以访问的知识库</p>
        <div className="min-h-[200px]">
          {isLoadingKnowledgeBases ? (
            <div className="grid grid-cols-2 gap-4 mt-4">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-20 w-full rounded-lg" />
              ))}
            </div>
          ) : knowledgeBases.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              <p>暂无可用的知识库</p>
              <p className="text-sm mt-1">请先创建或安装知识库</p>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4 mt-4">
              {knowledgeBases.map((kb) => (
                <div
                  key={kb.id}
                  className={`border rounded-lg p-4 cursor-pointer transition-all ${
                    formData.knowledgeBaseIds.includes(kb.id)
                      ? "border-primary bg-primary/10"
                      : "hover:border-border"
                  }`}
                  onClick={() => onKnowledgeBaseClick(kb)}
                >
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-medium">{kb.name}</h3>
                    <Switch
                      checked={formData.knowledgeBaseIds.includes(kb.id)}
                      onCheckedChange={() => toggleKnowledgeBase(kb.id, kb.name)}
                      onClick={(e) => e.stopPropagation()}
                    />
                  </div>
                  <p className="text-sm text-muted-foreground">{kb.description || "暂无描述"}</p>
                  <p className="text-xs text-muted-foreground mt-1">
                    {kb.fileCount} 个文件
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default AgentToolsForm;
