# 以 Streamable HTTP 为 MCP 新连接目标

当前 Agent 工具连接使用 HTTP SSE，而项目需要面向可复用的远程 MCP 服务。我们决定让新连接优先采用标准 Streamable HTTP，并在迁移期保留旧 SSE 适配；GitHub Issue 示例通过通用 MCP 工具接入，不在 Agent 核心写死 GitHub。相比只保留 SSE，这增加了暂时的双协议维护成本，但可逐步迁移现有服务并避免把旧传输继续作为新集成的基础。
