"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { AgentRun, RunService } from "@/lib/run-service"

interface RunReconciliationPanelProps {
  run: AgentRun
  onResolved: (run: AgentRun) => void
}

/** Owner actions for resolving an uncertain external write with a durable evidence record. */
export function RunReconciliationPanel({ run, onResolved }: RunReconciliationPanelProps) {
  const [evidence, setEvidence] = useState("")
  const [evidenceReference, setEvidenceReference] = useState("")
  const [externalResourceId, setExternalResourceId] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const approval = run.approval

  if (run.status !== "WAITING_RECONCILIATION" || approval?.status !== "RECONCILIATION_REQUIRED") return null

  async function resolve(outcome: "RECORD_CREATED" | "CONFIRM_NOT_CREATED" | "STOP") {
    if (!approval || saving) return
    setError(null)
    if (!evidence.trim()) {
      setError("请填写核对依据或停止原因。")
      return
    }
    if (outcome === "CONFIRM_NOT_CREATED" && !evidenceReference.trim()) {
      setError("确认未创建并重试时必须填写可核验的依据引用。")
      return
    }
    if (outcome === "RECORD_CREATED" && !externalResourceId.trim()) {
      setError("记录已创建结果时必须填写资源 ID 或目标仓库中的 Issue URL。")
      return
    }
    setSaving(true)
    try {
      const response = await RunService.resolveReconciliation(run.id, approval.id, outcome, evidence.trim(),
        evidenceReference.trim() || undefined, externalResourceId.trim() || undefined)
      if (response.code !== 200 || !response.data) {
        setError(response.message || "保存结果核对失败")
        return
      }
      onResolved(response.data)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存结果核对失败")
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="space-y-3 rounded-md border border-amber-600/60 bg-amber-50/40 p-4 dark:bg-amber-950/10"
      aria-label="外部写入结果核对" aria-busy={saving}>
      <div>
        <h4 className="font-medium">外部写入结果核对</h4>
        <p className="text-sm text-muted-foreground">
          {approval.reconciliationReason || "写入响应丢失或结果无法确认。系统不会自动重发此操作。"}
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          一次 GitHub 搜索没有找到匹配项，不能证明 Issue 未创建。确认未创建并重试时必须提供可核验的依据引用，例如服务商投递记录 ID 或本仓库的 Issue URL。
        </p>
      </div>
      {approval.reconciliationOutcome && (
        <dl className="grid gap-1 text-xs sm:grid-cols-[7rem_minmax(0,1fr)]" aria-label="最近核对记录">
          <dt className="text-muted-foreground">最近结果</dt>
          <dd>{approval.reconciliationOutcome} · {approval.reconciliationAt || "时间未知"} · {approval.reconciliationActorId || "系统核对"}</dd>
          {approval.reconciliationEvidence && <>
            <dt className="text-muted-foreground">记录依据</dt>
            <dd className="whitespace-pre-wrap break-words">{approval.reconciliationEvidence}</dd>
          </>}
          {approval.reconciliationEvidenceReference && <>
            <dt className="text-muted-foreground">依据引用</dt>
            <dd className="break-all">{approval.reconciliationEvidenceReference}</dd>
          </>}
        </dl>
      )}
      <label className="block space-y-1 text-sm">
        <span>核对依据或停止原因（必填，最多 2,000 字）</span>
        <Textarea maxLength={2000} rows={3} value={evidence} onChange={event => setEvidence(event.target.value)} />
      </label>
      <label className="block space-y-1 text-sm">
        <span>未创建依据引用（确认未创建时必填，最多 512 字）</span>
        <input className="w-full rounded-md border bg-background px-3 py-2 text-sm" maxLength={512}
          value={evidenceReference} onChange={event => setEvidenceReference(event.target.value)}
          placeholder="记录 ID 或 https://github.com/owner/repo/issues/42" />
      </label>
      <label className="block space-y-1 text-sm">
        <span>已创建资源 ID 或本仓库 Issue URL</span>
        <input className="w-full rounded-md border bg-background px-3 py-2 text-sm" maxLength={512}
          value={externalResourceId} onChange={event => setExternalResourceId(event.target.value)}
          placeholder="例如：42 或 https://github.com/owner/repo/issues/42" />
      </label>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <Button type="button" onClick={() => void resolve("RECORD_CREATED")} disabled={saving || !evidence.trim() || !externalResourceId.trim()}>
          记录已创建
        </Button>
        <Button type="button" variant="outline" onClick={() => void resolve("CONFIRM_NOT_CREATED")}
          disabled={saving || !evidence.trim() || !evidenceReference.trim()}>
          确认未创建并重试
        </Button>
        <Button type="button" variant="outline" onClick={() => void resolve("STOP")} disabled={saving || !evidence.trim()}>
          停止并保留记录
        </Button>
      </div>
    </section>
  )
}
