"use client"

import { FormEvent, useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { AgentRun, RunService } from "@/lib/run-service"

interface RunPanelProps {
  sessionId: string
}

const statusNames: Record<string, string> = {
  PENDING: "待执行",
  RUNNING: "运行中",
  WAITING_APPROVAL: "待审批",
  WAITING_RECONCILIATION: "待核对",
  COMPLETED: "已完成",
  FAILED: "失败",
  CANCELLED: "已取消",
}

/** 当前会话中的独立目标及其持久运行。 */
export function RunPanel({ sessionId }: RunPanelProps) {
  const [goal, setGoal] = useState("")
  const [runs, setRuns] = useState<AgentRun[]>([])
  const [selectedRun, setSelectedRun] = useState<AgentRun | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

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

  async function selectRun(runId: string) {
    setError(null)
    const response = await RunService.detail(runId)
    if (response.code === 200) setSelectedRun(response.data)
    else setError(response.message || "加载运行详情失败")
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!goal.trim() || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const response = await RunService.start(sessionId, goal.trim())
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

  return (
    <section className="flex h-full min-h-0 flex-col overflow-y-auto p-6" aria-label="目标运行">
      <div className="mx-auto w-full max-w-4xl space-y-6">
        <div>
          <h2 className="text-xl font-semibold">目标运行</h2>
          <p className="text-sm text-muted-foreground">每个目标独立记录状态、结果与步骤。</p>
        </div>
        <form onSubmit={submit} className="space-y-3">
          <label htmlFor="run-goal" className="block text-sm font-medium">目标</label>
          <Textarea id="run-goal" value={goal} onChange={event => setGoal(event.target.value)}
            placeholder="描述希望 Agent 完成的工作" rows={3} />
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
                <p className="whitespace-pre-wrap text-sm">{selectedRun.result || selectedRun.errorMessage || "等待结果"}</p>
                <div>
                  <h4 className="mb-2 text-sm font-medium">步骤</h4>
                  <ol className="space-y-2">
                    {selectedRun.steps?.map(step => (
                      <li key={step.id} className="rounded border p-2 text-sm">
                        {step.sequenceNo}. {step.kind} · {statusNames[step.status] || step.status}
                        {step.errorMessage && <p className="text-destructive">{step.errorMessage}</p>}
                      </li>
                    ))}
                  </ol>
                </div>
              </div>
            ) : <p className="text-sm text-muted-foreground">选择一次运行查看详情</p>}
          </div>
        </div>
      </div>
    </section>
  )
}
