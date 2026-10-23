"use client"

import { useEffect, useRef, useState, type FormEvent } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { ReviewReport } from "@/components/reviews-report"
import { PrReviewService, type PrReview } from "@/lib/pr-review-service"
import { prInputService, type PrInputSnapshot } from "@/lib/pr-input-service"
import { getAllModels, type Model } from "@/lib/user-settings-service"

function data<T>(response: { code: number; message: string; data: T }): T {
  if (response.code !== 200) throw new Error(response.message || "操作失败")
  return response.data
}
const states = { PENDING: "等待生成", RUNNING: "正在审查", SUCCEEDED: "报告已保存", FAILED: "审查失败" }

export default function ReviewReportsPage() {
  const [input, setInput] = useState<PrInputSnapshot | null>(null)
  const [models, setModels] = useState<Model[]>([])
  const [modelId, setModelId] = useState("")
  const [history, setHistory] = useState<PrReview[]>([])
  const [report, setReport] = useState<PrReview | null>(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState("")
  const sequence = useRef(0)

  function show(item: PrReview) {
    setReport(item)
    setHistory(current => [item, ...current.filter(saved => saved.id !== item.id)])
    window.history.replaceState(null, "", `?input=${encodeURIComponent(item.inputRevisionId)}&report=${encodeURIComponent(item.id)}`)
  }
  useEffect(() => {
    let active = true
    const query = new URLSearchParams(window.location.search)
    async function load() {
      try {
        const [saved, configured] = await Promise.all([PrReviewService.list(), getAllModels()])
        if (!active) return
        setHistory(data(saved)); setModels(data(configured).filter(item => item.status && item.type === "CHAT"))
        const id = query.get("report"), source = query.get("input")
        const item = id ? data(await PrReviewService.get(id)) : null
        const selected = item ? item.inputRevisionId : source
        const snapshot = selected ? data(await prInputService.get(selected)) : null
        if (!active) return
        setInput(snapshot); if (item) show(item)
      } catch (cause) { if (active) setError(cause instanceof Error ? cause.message : "读取审查目标失败") }
      finally { if (active) setBusy(false) }
    }
    void load()
    return () => { active = false; sequence.current = -1 }
  }, [])

  async function open(id: string) {
    const current = ++sequence.current
    setBusy(true); setError("")
    try {
      const item = data(await PrReviewService.get(id))
      const selected = data(await prInputService.get(item.inputRevisionId))
      if (current !== sequence.current) return
      setInput(selected); show(item)
    } catch (cause) { if (current === sequence.current) setError(cause instanceof Error ? cause.message : "读取失败") }
    finally { if (current === sequence.current) setBusy(false) }
  }
  async function create(event: FormEvent) {
    event.preventDefault(); if (!input || !modelId) return
    const current = ++sequence.current
    setBusy(true); setError("")
    try {
      const pending = data(await PrReviewService.create(input.id, modelId))
      if (current !== sequence.current) return
      show(pending)
      const generated = data(await PrReviewService.generate(pending.id))
      if (current === sequence.current) show(generated)
    } catch (cause) { if (current === sequence.current) setError(cause instanceof Error ? cause.message : "生成失败；保存的目标可刷新查看") }
    finally { if (current === sequence.current) setBusy(false) }
  }
  async function generate() {
    if (!report) return
    const current = ++sequence.current
    setBusy(true); setError("")
    try {
      const generated = data(await PrReviewService.generate(report.id))
      if (current === sequence.current) show(generated)
    } catch (cause) { if (current === sequence.current) setError(cause instanceof Error ? cause.message : "审查失败") }
    finally { if (current === sequence.current) setBusy(false) }
  }
  async function download() {
    if (!report?.currentRevisionId) return
    setBusy(true); setError("")
    try {
      const blob = await PrReviewService.markdown(report.id, report.currentRevisionId)
      const url = URL.createObjectURL(blob), anchor = document.createElement("a")
      anchor.href = url; anchor.download = "pr-review.md"; anchor.click()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (cause) { setError(cause instanceof Error ? cause.message : "下载失败") }
    finally { setBusy(false) }
  }

  return <main className="container max-w-5xl space-y-6 px-4 py-8">
    <header className="space-y-2"><Link href="/reviews" className="text-sm text-primary underline">读取新的 PR 输入</Link>
      <h1 className="text-3xl font-semibold tracking-tight">Bug 审查报告</h1>
      <p className="text-muted-foreground">固定输入、独立运行，保存可复核的源码证据。</p></header>
    {error && <p role="alert" aria-label="报告操作错误" className="rounded-md border border-destructive/40 p-3 text-sm text-destructive">{error}</p>}
    <section className="space-y-4 rounded-lg border p-5" aria-label="审查目标">
      <h2 className="text-lg font-semibold">选择固定输入与模型</h2>
      {input ? <div className="min-w-0 space-y-2 text-sm"><p>{input.repository} #{input.pullNumber} · {input.title}</p>
        <p className="break-all font-mono text-xs">head {input.headSha}</p>
        <Link href={`/reviews?input=${encodeURIComponent(input.id)}`} className="text-primary underline">查看本次固定源码、差异与信息缺口</Link></div>
        : <p className="text-sm text-muted-foreground">请先读取并固定 PR 输入，或打开下方已保存的目标。</p>}
      <form onSubmit={create} className="flex flex-col items-start gap-3 sm:flex-row sm:items-end">
        <div className="w-full space-y-2 sm:max-w-sm"><Label htmlFor="review-model">审查模型</Label>
          <select id="review-model" value={modelId} onChange={event => setModelId(event.target.value)} disabled={busy} className="w-full rounded-md border bg-background p-2">
            <option value="">请选择已配置的对话模型</option>{models.map(model => <option key={model.id} value={model.id}>{model.name}</option>)}
          </select></div>
        <Button type="submit" disabled={busy || !input || !modelId}>新建并生成审查报告</Button>
      </form>
      {!busy && !models.length && <Link href="/settings" className="text-sm text-primary underline">先配置可用模型</Link>}
      {busy && <p role="status" className="text-sm text-muted-foreground">正在处理，请稍候…</p>}
    </section>
    {report && <section className="min-w-0 space-y-4 rounded-lg border p-5" aria-label="保存的审查报告">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-xl font-semibold">{states[report.run.status]}</h2>
        <div className="flex flex-wrap gap-2"><Button variant="outline" disabled={busy} onClick={() => open(report.id)}>刷新状态</Button>
          {report.run.status === "PENDING" && <Button disabled={busy} onClick={generate}>生成已保存目标</Button>}
          {report.currentRevisionId && <Button disabled={busy} onClick={download}>下载此修订 Markdown</Button>}</div></div>
      <p className="break-all text-xs text-muted-foreground">Run：{report.run.id} · 修订：{report.currentRevisionId || "尚未生成"}</p>
      {report.run.error && <p role="alert" aria-label="审查运行错误" className="text-sm text-destructive">{report.run.error}</p>}
      {report.revision && <ReviewReport report={report.revision.content} />}
    </section>}
    <section className="space-y-3"><h2 className="text-lg font-semibold">保存的审查目标</h2>
      {!history.length && !busy && <p className="text-sm text-muted-foreground">尚无审查目标。</p>}
      <ul className="divide-y rounded-md border">{history.map(item => <li key={item.id}><button disabled={busy} onClick={() => open(item.id)} className="w-full space-y-1 p-4 text-left hover:bg-muted/40 disabled:opacity-50"><span className="block text-sm font-medium">{item.parameters.repository} #{item.parameters.pullNumber} · {states[item.run.status]}</span><span className="block break-all text-xs text-muted-foreground">{item.parameters.headSha} · {new Date(item.createdAt).toLocaleString("zh-CN")}</span></button></li>)}</ul>
    </section>
  </main>
}
