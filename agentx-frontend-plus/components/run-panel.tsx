"use client"

import { FormEvent, useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { GitHubIssueResult } from "@/components/github-issue-result"
import { githubConnectionService } from "@/lib/github-connection-service"
import { AgentRun, KnowledgeCitation, KnowledgeRetrievalResult, RunService } from "@/lib/run-service"
import { RunReconciliationPanel } from "@/components/run-reconciliation-panel"
import { RunTimeline } from "@/components/run-timeline"

interface RunPanelProps {
  sessionId: string
  agentName?: string
  knowledgeBaseIds?: string[]
}

const statusNames: Record<string, string> = {
  PENDING: "待执行",
  RUNNING: "运行中",
  WAITING_APPROVAL: "待审批",
  WAITING_RECONCILIATION: "待核对",
  WAITING_SCHEMA_REVIEW: "工具结构待核对",
  COMPLETED: "已完成",
  FAILED: "失败",
  CANCELLED: "已取消",
}
const NO_KNOWLEDGE_BASES: string[] = []

/** 当前会话中的独立目标及其持久运行。 */
export function RunPanel({ sessionId, agentName, knowledgeBaseIds = NO_KNOWLEDGE_BASES }: RunPanelProps) {
  const [goal, setGoal] = useState("")
  const [approvalPolicy, setApprovalPolicy] = useState<AgentRun["approvalPolicy"]>("CREATOR_DEFAULT")
  const [knowledgeBaseId, setKnowledgeBaseId] = useState("")
  const [repositories, setRepositories] = useState<string[]>([])
  const [targetRepository, setTargetRepository] = useState("")
  const [runs, setRuns] = useState<AgentRun[]>([])
  const [selectedRun, setSelectedRun] = useState<AgentRun | null>(null)
  const [knowledgeResult, setKnowledgeResult] = useState<KnowledgeRetrievalResult | null>(null)
  const [knowledgeLoading, setKnowledgeLoading] = useState(false)
  const [selectedCitation, setSelectedCitation] = useState<{ runId: string; citation: KnowledgeCitation } | null>(null)
  const [citationLoading, setCitationLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [decidingApproval, setDecidingApproval] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setKnowledgeBaseId(current => knowledgeBaseIds.includes(current) ? current : knowledgeBaseIds[0] || "")
  }, [knowledgeBaseIds])

  useEffect(() => {
    let active = true
    githubConnectionService.get().then(response => {
      if (!active || response.code !== 200) return
      const allowed = response.data?.repositories || []
      setRepositories(allowed)
      setTargetRepository(current => allowed.includes(current) ? current : allowed[0] || "")
    }).catch(() => {
      if (active) setRepositories([])
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    setLoading(true)
    setSelectedRun(null)
    setError(null)
    RunService.list(sessionId).then(async response => {
      if (!active) return
      if (response.code !== 200) {
        setError(response.message || "加载运行失败")
        return
      }
      setRuns(response.data || [])
      if (response.data?.length) {
        const detail = await RunService.detail(response.data[0].id)
        if (active && detail.code === 200) setSelectedRun(detail.data)
      }
    }).catch(cause => {
      if (active) setError(cause instanceof Error ? cause.message : "加载运行失败")
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [sessionId])

  useEffect(() => {
    let active = true
    setKnowledgeResult(null)
    setSelectedCitation(null)
    if (!selectedRun?.knowledgeVersionId) {
      setKnowledgeLoading(false)
      return () => { active = false }
    }
    setKnowledgeLoading(true)
    RunService.knowledge(selectedRun.id, selectedRun.goal).then(response => {
      if (!active) return
      if (response.code === 200) setKnowledgeResult(response.data)
      else setError(response.message || "加载资料引用失败")
    }).catch(cause => {
      if (active) setError(cause instanceof Error ? cause.message : "加载资料引用失败")
    }).finally(() => {
      if (active) setKnowledgeLoading(false)
    })
    return () => { active = false }
  }, [selectedRun?.id, selectedRun?.goal, selectedRun?.knowledgeVersionId])

  const selectedRunId = selectedRun?.id
  const selectedRunStatus = selectedRun?.status

  useEffect(() => {
    if (!selectedRunId || !selectedRunStatus
      || ["COMPLETED", "FAILED", "CANCELLED", "WAITING_SCHEMA_REVIEW"].includes(selectedRunStatus)) return
    const runId = selectedRunId
    let active = true
    let refreshing = false
    const timer = setInterval(async () => {
      if (refreshing) return
      refreshing = true
      try {
        const response = await RunService.detail(runId)
        if (!active || response.code !== 200 || !response.data) return
        setSelectedRun(current => current?.id === runId ? response.data : current)
        setRuns(current => current.map(run => run.id === runId ? response.data : run))
      } catch {
        // 暂时断线时保留上一次持久状态；下一轮继续读取。
      } finally {
        refreshing = false
      }
    }, 2000)
    return () => {
      active = false
      clearInterval(timer)
    }
  }, [selectedRunId, selectedRunStatus])

  async function selectRun(runId: string) {
    setError(null)
    const response = await RunService.detail(runId)
    if (response.code === 200) setSelectedRun(response.data)
    else setError(response.message || "加载运行详情失败")
  }

  async function openCitation(documentId: string) {
    if (!selectedRun || citationLoading) return
    const runId = selectedRun.id
    setCitationLoading(true)
    setError(null)
    try {
      const response = await RunService.citation(runId, documentId)
      if (response.code !== 200 || !response.data) {
        setError(response.message || "读取引用资料失败")
        return
      }
      setSelectedCitation({ runId, citation: response.data })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "读取引用资料失败")
    } finally {
      setCitationLoading(false)
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!goal.trim() || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const response = await RunService.start(sessionId, goal.trim(), approvalPolicy,
        knowledgeBaseId || null, targetRepository || null)
      if (response.code !== 200 || !response.data) {
        setError(response.message || "发起运行失败")
        return
      }
      setRuns(previous => [response.data, ...previous])
      setSelectedRun(response.data)
      setGoal("")
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "发起运行失败")
    } finally {
      setSubmitting(false)
    }
  }

  async function cancelSelectedRun() {
    if (!selectedRun || cancelling) return
    setCancelling(true)
    setError(null)
    try {
      const response = await RunService.cancel(selectedRun.id)
      if (response.code !== 200 || !response.data) {
        setError(response.message || "取消运行失败")
        return
      }
      const cancelledRun = response.data
      setSelectedRun(cancelledRun)
      setRuns(previous => previous.map(run => run.id === cancelledRun.id ? cancelledRun : run))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "取消运行失败")
    } finally {
      setCancelling(false)
    }
  }

  async function decideApproval(decision: "APPROVE" | "DENY") {
    const approval = selectedRun?.approval
    if (!selectedRun || !approval || approval.status !== "PENDING" || decidingApproval) return
    setDecidingApproval(true)
    setError(null)
    try {
      const response = await RunService.decideApproval(selectedRun.id, approval.id, decision)
      if (response.code !== 200 || !response.data) {
        setError(response.message || "提交审批决定失败")
        return
      }
      setSelectedRun(response.data)
      setRuns(previous => previous.map(run => run.id === response.data.id ? response.data : run))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "提交审批决定失败")
    } finally {
      setDecidingApproval(false)
    }
  }

  return (
    <section className="flex h-full min-h-0 flex-col overflow-y-auto p-6" aria-label="目标运行">
      <div className="mx-auto w-full max-w-4xl space-y-6">
        <div>
          <h2 className="text-xl font-semibold">目标运行</h2>
          <p className="text-sm text-muted-foreground">Agent：{agentName || "当前工作区 Agent"} · 每个目标独立记录状态、结果与步骤。</p>
        </div>
        <form onSubmit={submit} className="space-y-3">
          <label htmlFor="run-goal" className="block text-sm font-medium">目标</label>
          <Textarea id="run-goal" value={goal} onChange={event => setGoal(event.target.value)}
            placeholder="描述希望 Agent 完成的工作" rows={3} />
          <label htmlFor="run-knowledge-base" className="block text-sm font-medium">知识库</label>
          <select id="run-knowledge-base" className="w-full rounded-md border bg-background px-3 py-2 text-sm"
            value={knowledgeBaseId} disabled={knowledgeBaseIds.length === 0}
            onChange={event => setKnowledgeBaseId(event.target.value)}>
            {knowledgeBaseIds.length === 0
              ? <option value="">当前 Agent 未配置知识库</option>
              : knowledgeBaseIds.map((id, index) => <option key={id} value={id}>知识库 {index + 1} · {id}</option>)}
          </select>
          <label htmlFor="run-target-repository" className="block text-sm font-medium">GitHub 目标仓库</label>
          <select id="run-target-repository" className="w-full rounded-md border bg-background px-3 py-2 text-sm"
            value={targetRepository} disabled={repositories.length === 0}
            onChange={event => setTargetRepository(event.target.value)}>
            {repositories.length === 0
              ? <option value="">请先在 GitHub 设置中连接并授权仓库</option>
              : repositories.map(repository => <option key={repository} value={repository}>{repository}</option>)}
          </select>
          <label htmlFor="run-approval-policy" className="block text-sm font-medium">外部写入审批</label>
          <select id="run-approval-policy" className="w-full rounded-md border bg-background px-3 py-2 text-sm"
            value={approvalPolicy}
            onChange={event => setApprovalPolicy(event.target.value as AgentRun["approvalPolicy"])}>
            <option value="CREATOR_DEFAULT">使用创建者逐项预授权；其他写入需审批</option>
            <option value="REQUIRE_APPROVAL_FOR_ALL_WRITES">所有外部写入都需审批</option>
          </select>
          <p className="text-xs text-muted-foreground">
            此运行可以要求更多审批；创建者的预授权规则不能被放宽。未分类动作始终需要审批。
          </p>
          <Button type="submit" disabled={!goal.trim() || submitting}>
            {submitting ? "运行中..." : "开始运行"}
          </Button>
        </form>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)]">
          <div>
            <h3 className="mb-3 font-medium">运行列表</h3>
            {loading ? <p className="text-sm text-muted-foreground">加载中...</p> : runs.length === 0 ? (
              <p className="text-sm text-muted-foreground">暂无运行</p>
            ) : (
              <ul className="space-y-2">
                {runs.map(run => (
                  <li key={run.id}>
                    <button type="button" onClick={() => void selectRun(run.id)}
                      className="w-full rounded-md border p-3 text-left hover:bg-muted/40"
                      aria-current={selectedRun?.id === run.id ? "true" : undefined}>
                      <span className="block truncate font-medium">{run.goal}</span>
                      <span className="text-xs text-muted-foreground">
                        {statusNames[run.status] || run.status} · {run.id}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div>
            <h3 className="mb-3 font-medium">运行详情</h3>
            {selectedRun ? (
              <div className="space-y-4 rounded-md border p-4">
                <p className="text-sm font-medium">运行 {selectedRun.id}</p>
                <p className="text-xs text-muted-foreground">会话 {selectedRun.sessionId} · {statusNames[selectedRun.status] || selectedRun.status}</p>
                <p className="text-xs text-muted-foreground" aria-label="当前运行的外部写入审批规则">
                  {selectedRun.approvalPolicy === "REQUIRE_APPROVAL_FOR_ALL_WRITES"
                    ? "当前规则：所有外部写入均需审批。"
                    : "当前规则：按创建者逐项预授权执行；其他或未分类写入需审批。"}
                </p>
                {selectedRun.approval && (
                  <section className="space-y-3 rounded-md border border-amber-500/50 bg-amber-50/40 p-4 dark:bg-amber-950/10"
                    aria-label="外部写入审批" aria-busy={decidingApproval}>
                    <div>
                      <h4 className="font-medium">外部写入请求</h4>
                      <p className="text-sm text-muted-foreground">
                        {selectedRun.approval.status === "PENDING" ? "此固定操作等待运行所有者审批。" :
                          selectedRun.approval.status === "APPROVED" && selectedRun.status === "COMPLETED" ? "已执行所有者批准的固定操作。" :
                            selectedRun.approval.status === "APPROVED" ? "所有者已批准此固定操作。" :
                              selectedRun.approval.status === "EXECUTING" ? "已批准操作正在执行。" :
                            selectedRun.approval.status === "DENIED" ? "所有者已拒绝此操作。" :
                              selectedRun.approval.status === "EXECUTED" ? "已执行所有者批准的固定操作。" :
                                selectedRun.approval.status === "RECONCILIATION_REQUIRED" ? "批准的写入结果不确定，等待核对。" :
                              `审批状态：${selectedRun.approval.status}`}
                      </p>
                    </div>
                    <dl className="grid gap-2 text-sm sm:grid-cols-[8rem_minmax(0,1fr)]">
                      <dt className="text-muted-foreground">动作</dt>
                      <dd className="break-words">{selectedRun.approval.actionDescription || selectedRun.approval.actionName}
                        <span className="ml-2 font-mono text-xs text-muted-foreground">{selectedRun.approval.actionName}</span>
                      </dd>
                      <dt className="text-muted-foreground">服务</dt>
                      <dd className="break-all font-mono text-xs">{selectedRun.approval.serviceId}</dd>
                      <dt className="text-muted-foreground">目标仓库</dt>
                      <dd className="break-all font-mono">{selectedRun.approval.targetRepository}</dd>
                      <dt className="text-muted-foreground">不可变审批预览</dt>
                      <dd><pre className="overflow-x-auto whitespace-pre-wrap break-words rounded bg-background p-3 font-mono text-xs">{selectedRun.approval.arguments}</pre></dd>
                      {selectedRun.approval.arguments.includes("hi-agent-action-intent:") && (
                        <>
                          <dt className="text-muted-foreground">关联标记</dt>
                          <dd className="text-xs text-muted-foreground">
                            正文中的 HTML 注释标记不会显示在 GitHub Issue 页面，用于将这次批准与结果核对关联。
                          </dd>
                        </>
                      )}
                      {selectedRun.approval.externalResourceId && (
                        <>
                          <dt className="text-muted-foreground">外部资源 ID</dt>
                          <dd><GitHubIssueResult externalResourceId={selectedRun.approval.externalResourceId}
                            repository={selectedRun.approval.targetRepository} /></dd>
                        </>
                      )}
                    </dl>
                    {selectedRun.status === "WAITING_APPROVAL" && selectedRun.approval.status === "PENDING" && (
                      <div className="flex flex-wrap gap-2">
                        <Button type="button" onClick={() => void decideApproval("APPROVE")} disabled={decidingApproval}>
                          {decidingApproval ? "提交中..." : "批准并继续"}
                        </Button>
                        <Button type="button" variant="outline" onClick={() => void decideApproval("DENY")} disabled={decidingApproval}>
                          {decidingApproval ? "提交中..." : "拒绝写入"}
                        </Button>
                      </div>
                    )}
                  </section>
                )}
                <RunReconciliationPanel run={selectedRun} onResolved={resolvedRun => {
                  setSelectedRun(resolvedRun)
                  setRuns(previous => previous.map(run => run.id === resolvedRun.id ? resolvedRun : run))
                }} />
                {["PENDING", "RUNNING", "WAITING_APPROVAL", "WAITING_SCHEMA_REVIEW"].includes(selectedRun.status)
                  && !["EXECUTING", "EXECUTED"].includes(selectedRun.approval?.status || "") && (
                  <Button type="button" variant="outline" onClick={() => void cancelSelectedRun()} disabled={cancelling}>
                    {cancelling ? "取消中..." : "取消运行"}
                  </Button>
                )}
                <p className="whitespace-pre-wrap text-sm">{selectedRun.result || selectedRun.errorMessage || "等待结果"}</p>
                {selectedRun.knowledgeVersionId && (
                  <div className="space-y-2 rounded border bg-muted/20 p-3" aria-label="资料引用">
                    <h4 className="text-sm font-medium">固定知识版本与引用</h4>
                    <p className="text-xs text-muted-foreground">版本 {selectedRun.knowledgeVersionId}</p>
                    {knowledgeLoading ? <p className="text-sm text-muted-foreground">检索引用中...</p> : knowledgeResult ? (
                      <>
                        <p className={knowledgeResult.hasEvidence ? "text-sm" : "text-sm text-destructive"}>
                          {knowledgeResult.message}
                        </p>
                        {knowledgeResult.citations.length > 0 && (
                          <ul className="space-y-2">
                            {knowledgeResult.citations.map(citation => (
                              <li key={citation.documentId} className="text-sm">
                                <button type="button" className="underline text-left" disabled={citationLoading}
                                  onClick={() => void openCitation(citation.documentId)}>
                                  {citation.fileName || citation.fileId || "资料"} · 第 {citation.page ?? "?"} 页
                                </button>
                                <p className="line-clamp-3 text-xs text-muted-foreground">{citation.content}</p>
                              </li>
                            ))}
                          </ul>
                        )}
                        {selectedCitation?.runId === selectedRun.id && (
                          <div className="rounded border p-3" aria-label="引用资料全文">
                            <p className="text-sm font-medium">{selectedCitation.citation.fileName || "资料"} · 第 {selectedCitation.citation.page ?? "?"} 页</p>
                            <p className="whitespace-pre-wrap text-sm">{selectedCitation.citation.content}</p>
                          </div>
                        )}
                      </>
                    ) : null}
                  </div>
                )}
                <RunTimeline run={selectedRun} />
              </div>
            ) : <p className="text-sm text-muted-foreground">选择一次运行查看详情</p>}
          </div>
        </div>
      </div>
    </section>
  )
}
