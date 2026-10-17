import type { AgentRun, RunNextAction, RunTimelineEvent } from "@/lib/run-service"

const eventNames: Record<string, string> = {
  RUN_CREATED: "运行已创建",
  RUN_ATTEMPT_STARTED: "开始处理",
  KNOWLEDGE_RETRIEVAL: "固定资料检索",
  KNOWLEDGE_SOURCE: "资料来源",
  MODEL_CALL: "模型调用",
  TOOL_ACTION: "工具动作",
  APPROVAL_REQUESTED: "等待所有者审批",
  APPROVAL_DECISION: "审批决定",
  RECONCILIATION: "外部结果核对",
  TOOL_SCHEMA_REVIEW: "工具结构核对",
  RUN_COMPLETED: "运行已完成",
  RUN_FAILED: "运行失败",
  RUN_CANCELLED: "运行已取消",
}

const nextActionNames: Record<RunNextAction, string> = {
  OWNER_DECISION: "请审阅并决定是否批准外部写入。",
  REVIEW_TOOL_SCHEMA: "请检查工具结构后再重新运行。",
  RECONCILE_EXTERNAL_WRITE: "请核对外部写入是否已经完成。",
  ADD_OR_SELECT_EVIDENCE: "请补充或选择支持目标的资料后重新运行。",
  CHECK_REPOSITORY_AUTHORIZATION: "请检查目标仓库的授权和连接权限。",
  CHECK_MODEL_CONNECTION: "请检查模型连接设置。",
  START_NEW_RUN: "检查失败步骤后，可以发起一次新运行。",
  WAIT: "运行仍在处理中。",
  NONE: "无需后续操作。",
}

function eventDescription(event: RunTimelineEvent): string[] {
  const details = event.details
  const lines: string[] = []
  if (event.eventType === "MODEL_CALL") {
    if (details.modelName) lines.push(details.modelName)
    if (details.usageStatus === "AVAILABLE") {
      lines.push(`${details.inputTokens ?? 0} 输入 / ${details.outputTokens ?? 0} 输出 token`)
    } else {
      lines.push("提供方未返回 token 用量")
    }
    if (details.durationMs != null) lines.push(`调用耗时 ${details.durationMs} ms`)
  }
  if (event.eventType === "KNOWLEDGE_SOURCE") {
    lines.push([details.sourceName, details.sourcePage != null ? `第 ${details.sourcePage} 页` : null]
      .filter(Boolean).join(" · "))
  }
  if (event.eventType === "TOOL_ACTION" || event.eventType === "APPROVAL_REQUESTED"
    || event.eventType === "APPROVAL_DECISION" || event.eventType === "RECONCILIATION") {
    const action = [details.serviceId, details.actionName, details.targetRepository].filter(Boolean).join(" · ")
    if (action) lines.push(action)
    if (details.decision) lines.push(`决定：${details.decision}`)
    if (details.actorId) lines.push(`操作人：${details.actorId}`)
    if (details.resultReference) lines.push(`资源：${details.resultReference}`)
  }
  if (details.failureCode) lines.push(`失败类型：${details.failureCode}`)
  return lines
}

export function RunTimeline({ run }: { run: AgentRun }) {
  const events = [...(run.timeline || [])].sort((left, right) =>
    Date.parse(left.createdAt) - Date.parse(right.createdAt))

  return (
    <section className="space-y-3" aria-label="运行时间线">
      <div className="rounded border bg-muted/20 p-3" aria-label="下一步建议">
        <h4 className="text-sm font-medium">下一步</h4>
        <p className="text-sm text-muted-foreground">
          {run.nextAction ? nextActionNames[run.nextAction] : "当前运行状态暂无后续操作建议。"}
        </p>
      </div>
      {run.modelUsage && (
        <p className="text-xs text-muted-foreground" aria-label="模型用量">
          模型调用 {run.modelUsage.modelCallCount} 次 · 输入 {run.modelUsage.inputTokens} / 输出 {run.modelUsage.outputTokens} token
          {!run.modelUsage.complete && " · 部分调用未提供用量"}
        </p>
      )}
      {events.length > 0 ? (
        <ol className="space-y-2" aria-label="事件">
          {events.map(event => (
            <li key={event.id} className="rounded border p-3 text-sm">
              <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                <span className="font-medium">{eventNames[event.eventType] || "运行事件"}</span>
                <time className="text-xs text-muted-foreground" dateTime={event.createdAt}>
                  {event.createdAt}
                </time>
              </div>
              <p>{event.summary}</p>
              <p className="text-xs text-muted-foreground">状态：{event.status} · 运行 {event.runId}
                {event.stepId ? ` · 步骤 ${event.stepId}` : ""}</p>
              {eventDescription(event).filter(Boolean).map((line, index) => (
                <p key={`${event.id}-${index}`} className="break-words text-xs text-muted-foreground">{line}</p>
              ))}
            </li>
          ))}
        </ol>
      ) : (
        <p className="text-sm text-muted-foreground">此运行尚无时间线事件。</p>
      )}
    </section>
  )
}
