"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { TeachingService } from "@/lib/teaching-service"
import { TeachingOutlineService } from "@/lib/teaching-outline-service"
import { getAllModels, type Model } from "@/lib/user-settings-service"
import type { TeachingSelection } from "@/types/teaching"
import type { OutlineLesson, OutlineParameters, TeachingOutline } from "@/types/teaching-outline"

function data<T>(response: { code: number; message: string; data: T }): T {
  if (response.code !== 200) throw new Error(response.message || "操作失败，请刷新重试")
  return response.data
}
const defaultParameters: OutlineParameters = {
  subject: "数学", level: "高二", textbook: "人教版数学 选择性必修二", edition: "", module: "导数",
  lessonMinutes: 45, studentFoundation: "中等", totalLessons: 1, scopeConfirmed: false,
}
const runLabels = { PENDING: "等待生成", RUNNING: "正在生成", SUCCEEDED: "生成完成", FAILED: "生成失败" }

export default function TeachingOutlinesPage() {
  const [selections, setSelections] = useState<TeachingSelection[]>([])
  const [models, setModels] = useState<Model[]>([])
  const [selectionId, setSelectionId] = useState("")
  const [modelId, setModelId] = useState("")
  const [parameters, setParameters] = useState(defaultParameters)
  const [history, setHistory] = useState<TeachingOutline[]>([])
  const [outline, setOutline] = useState<TeachingOutline | null>(null)
  const [lessons, setLessons] = useState<OutlineLesson[]>([])
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")

  function show(item: TeachingOutline) {
    setOutline(item)
    setLessons(item.revision?.content.lessons.map(lesson => ({ ...lesson, objectives: [...lesson.objectives] })) || [])
    setDirty(false)
    setHistory(current => [item, ...current.filter(saved => saved.id !== item.id)])
    window.history.replaceState(null, "", `?outline=${encodeURIComponent(item.id)}`)
  }

  useEffect(() => {
    let active = true
    const query = new URLSearchParams(window.location.search)
    const id = query.get("outline")
    setSelectionId(query.get("selection") || "")
    Promise.allSettled([TeachingService.list(), getAllModels(), TeachingOutlineService.list(), id ? TeachingOutlineService.get(id) : Promise.resolve(null)] as const).then(results => {
      if (!active) return
      const errors: string[] = []
      const selected = results[0]
      try { if (selected.status === "fulfilled") setSelections(data(selected.value)); else throw selected.reason }
      catch (e) { errors.push(e instanceof Error ? e.message : "读取选材失败") }
      const available = results[1]
      try { if (available.status === "fulfilled") setModels(data(available.value).filter(model => model.status && model.type === "CHAT")); else throw available.reason }
      catch (e) { errors.push(e instanceof Error ? e.message : "读取模型失败") }
      const saved = results[2]
      try { if (saved.status === "fulfilled") setHistory(data(saved.value)); else throw saved.reason }
      catch (e) { errors.push(e instanceof Error ? e.message : "读取大纲历史失败") }
      const current = results[3]
      try { if (current.status === "fulfilled" && current.value) show(data(current.value)); else if (current.status === "rejected") throw current.reason }
      catch (e) { errors.push(e instanceof Error ? e.message : "读取大纲失败") }
      setError(errors.join("；")); setBusy(false)
    })
    return () => { active = false }
  }, [])

  function parameter<K extends keyof OutlineParameters>(key: K, value: OutlineParameters[K]) {
    setParameters(current => ({ ...current, [key]: value, ...(key !== "scopeConfirmed" ? { scopeConfirmed: false } : {}) }))
  }
  async function create(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(""); setNotice("")
    try {
      const saved = data(await TeachingOutlineService.create({ ...parameters, selectionId, modelId }))
      show(saved)
      const generated = data(await TeachingOutlineService.generate(saved.id))
      show(generated)
      setNotice(generated.run.status === "SUCCEEDED" ? "大纲已保存。请核对、修改并确认，再继续逐课材料。" : "运行状态已保存，请查看失败原因。")
    } catch (e) { setError(e instanceof Error ? e.message : "生成失败；已保存的目标可刷新查看") }
    finally { setBusy(false) }
  }
  async function open(id: string) {
    setBusy(true); setError(""); setNotice("")
    try { show(data(await TeachingOutlineService.get(id))) }
    catch (e) { setError(e instanceof Error ? e.message : "读取大纲失败") }
    finally { setBusy(false) }
  }
  async function update(action: "generate" | "save" | "confirm") {
    if (!outline) return
    setBusy(true); setError(""); setNotice("")
    try {
      const response = action === "generate" ? await TeachingOutlineService.generate(outline.id)
        : action === "save" ? await TeachingOutlineService.edit(outline.id, outline.currentRevisionId!, lessons)
        : await TeachingOutlineService.confirm(outline.id, outline.currentRevisionId!)
      show(data(response))
      setNotice(action === "confirm" ? "当前大纲修订已确认，可作为逐课材料的依据。" : action === "save" ? "修改已保存为新修订，请重新确认。" : "运行状态已更新。")
    } catch (e) { setError(e instanceof Error ? e.message : "操作失败") }
    finally { setBusy(false) }
  }

  return <main className="mx-auto w-full min-w-0 max-w-6xl space-y-6 px-4 py-8 md:px-8">
    <header className="space-y-2"><Link className="text-sm underline" href="/teaching">返回教学选材</Link><h1 className="text-3xl font-semibold">模块课时大纲</h1><p className="text-muted-foreground">先确认教材与教学范围，再生成整模块大纲。确认当前修订后才能继续逐课讲义与教案。</p></header>
    {error && <p role="alert" className="rounded-md border border-destructive p-3 text-destructive">{error}</p>}
    {notice && <p role="status" className="rounded-md border p-3">{notice}</p>}
    {busy && <p role="status" aria-live="polite">正在读取或生成，请稍候；目标和结果保存在服务端。</p>}
    <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(220px,1fr)]">
      <form className="space-y-4 rounded-lg border p-5" onSubmit={create}>
        <h2 className="text-xl font-semibold">教学参数</h2>
        <fieldset disabled={busy} className="space-y-4">
          <div className="space-y-2"><Label htmlFor="outline-selection">固定选材</Label><select id="outline-selection" required className="h-10 w-full rounded-md border bg-background px-3" value={selectionId} onChange={event => { setSelectionId(event.target.value); parameter("scopeConfirmed", false) }}><option value="">请选择已核查选材</option>{selections.map(item => <option key={item.id} value={item.id} disabled={item.status !== "READY"}>{item.title} · 版本 {item.versionNumber}{item.status !== "READY" ? "（来源待核查）" : ""}</option>)}</select><p className="text-sm text-muted-foreground">来源待核查的选材不能生成大纲。<Link className="underline" href="/teaching">核查资料</Link></p></div>
          <div className="space-y-2"><Label htmlFor="outline-model">生成模型</Label><select id="outline-model" required className="h-10 w-full rounded-md border bg-background px-3" value={modelId} onChange={event => setModelId(event.target.value)}><option value="">请选择已配置模型</option>{models.map(model => <option key={model.id} value={model.id}>{model.name}</option>)}</select>{!models.length && <p className="text-sm text-muted-foreground">暂无可用对话模型，请先到<Link className="underline" href="/settings/providers">模型设置</Link>配置。</p>}</div>
          <div className="grid gap-4 sm:grid-cols-2">{([['subject', '学科'], ['level', '年级或水平'], ['textbook', '教材名称'], ['edition', '教材版本与版次'], ['module', '模块主题'], ['studentFoundation', '学生基础']] as const).map(([key, label]) => <div className="space-y-2" key={key}><Label htmlFor={`outline-${key}`}>{label}</Label><Input id={`outline-${key}`} required maxLength={key === "textbook" ? 300 : key === "subject" || key === "level" ? 100 : 200} value={parameters[key]} onChange={event => parameter(key, event.target.value)} /></div>)}</div>
          <p className="text-sm text-muted-foreground">请填写 A/B 版及版次或年份；暂不能核对时，明确写“版本待核对”。系统不会据此宣称匹配某一教材版本。</p>
          <div className="grid gap-4 sm:grid-cols-2"><div className="space-y-2"><Label htmlFor="outline-minutes">每课时分钟数</Label><Input id="outline-minutes" required type="number" min={10} max={180} value={parameters.lessonMinutes} onChange={event => parameter("lessonMinutes", Number(event.target.value))} /></div><div className="space-y-2"><Label htmlFor="outline-count">模块总课时</Label><Input id="outline-count" required type="number" min={1} max={60} value={parameters.totalLessons} onChange={event => parameter("totalLessons", Number(event.target.value))} /></div></div>
          <label className="flex items-start gap-2 text-sm"><input className="mt-1" type="checkbox" required checked={parameters.scopeConfirmed} onChange={event => parameter("scopeConfirmed", event.target.checked)} />我已核对以上教材信息，并确认本次总课时安排；版本待核对时不视为已匹配指定教材。</label>
          <Button type="submit" disabled={!selectionId || !modelId || !parameters.scopeConfirmed}>生成模块大纲</Button>
        </fieldset>
      </form>
      <aside className="space-y-3 rounded-lg border p-5"><h2 className="text-lg font-semibold">已保存大纲</h2>{!history.length && <p className="text-sm text-muted-foreground">生成目标将在这里保存，刷新后可继续查看。</p>}<ul className="space-y-2">{history.map(item => <li key={item.id}><button className="w-full rounded-md border p-3 text-left hover:bg-muted focus-visible:outline focus-visible:outline-2" disabled={busy} onClick={() => open(item.id)}><span className="block font-medium">{item.parameters.module}</span><span className="text-sm text-muted-foreground">{item.parameters.totalLessons} 课时 · {runLabels[item.run.status]} · {item.status === "CONFIRMED" ? "已确认" : "待确认"}</span></button></li>)}</ul></aside>
    </div>
    {outline && <section aria-label="大纲结果" className="space-y-4 rounded-lg border p-5">
      <h2 className="text-xl font-semibold">{outline.parameters.module} · {outline.parameters.totalLessons} 课时大纲</h2>
      <p className="break-all text-sm">固定知识版本：{outline.knowledgeVersionId} · 运行：{outline.run.id}</p>
      <p className="text-sm">{outline.parameters.textbook} / {outline.parameters.edition} · {outline.parameters.level} · 每课时 {outline.parameters.lessonMinutes} 分钟 · {outline.parameters.studentFoundation}基础</p>
      <p role="status">{runLabels[outline.run.status]} · {outline.status === "CONFIRMED" ? "当前修订已确认" : "尚未确认，不能作为逐课生成依据"}</p>
      {outline.run.error && <p role="alert" className="text-destructive">{outline.run.error}</p>}
      {outline.run.status === "PENDING" && <Button disabled={busy} onClick={() => update("generate")}>继续生成大纲</Button>}
      {outline.run.status === "RUNNING" && <Button variant="outline" disabled={busy} onClick={() => open(outline.id)}>刷新运行状态</Button>}
      {outline.run.status === "FAILED" && <p className="text-sm text-muted-foreground">请检查模型与来源后，通过上方参数创建新的生成目标；失败记录保留。</p>}
      <div className="space-y-4">{lessons.map((lesson, index) => <fieldset key={index} disabled={busy} className="space-y-3 rounded-md bg-muted/40 p-4"><legend className="px-1 font-medium">第 {index + 1} 课时</legend><Label htmlFor={`lesson-title-${index}`}>课时主题</Label><Input id={`lesson-title-${index}`} maxLength={300} value={lesson.title} onChange={event => { setLessons(current => current.map((item, at) => at === index ? { ...item, title: event.target.value } : item)); setDirty(true) }} /><Label htmlFor={`lesson-goals-${index}`}>学习目标（每行一个）</Label><Textarea id={`lesson-goals-${index}`} value={lesson.objectives.join("\n")} onChange={event => { setLessons(current => current.map((item, at) => at === index ? { ...item, objectives: event.target.value.split("\n") } : item)); setDirty(true) }} /></fieldset>)}</div>
      {outline.revision && <><p className="break-all text-xs text-muted-foreground">当前产物修订：{outline.currentRevisionId}{outline.revision.parentRevisionId && ` · 上一修订：${outline.revision.parentRevisionId}`}</p><div className="flex flex-wrap gap-3"><Button variant="outline" disabled={busy || !dirty || lessons.some(lesson => !lesson.title.trim() || !lesson.objectives.length || lesson.objectives.some(goal => !goal.trim()))} onClick={() => update("save")}>保存大纲修改</Button><Button disabled={busy || dirty || outline.status === "CONFIRMED"} onClick={() => update("confirm")}>确认当前大纲</Button>{dirty && <p className="text-sm text-muted-foreground">请先保存修改，再确认新修订。</p>}</div></>}
    </section>}
  </main>
}
