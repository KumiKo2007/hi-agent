"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { LessonPreview } from "@/components/teaching/lesson-preview"
import { TeachingOutlineService } from "@/lib/teaching-outline-service"
import { TeachingLessonService } from "@/lib/teaching-lesson-service"
import { getAllModels, type Model } from "@/lib/user-settings-service"
import type { TeachingOutline } from "@/types/teaching-outline"
import type { TeachingLesson } from "@/types/teaching-lesson"

function data<T>(response: { code: number; message: string; data: T }): T {
  if (response.code !== 200) throw new Error(response.message || "操作失败，请刷新重试")
  return response.data
}
const runLabels = { PENDING: "等待生成", RUNNING: "正在生成", SUCCEEDED: "生成完成", FAILED: "生成失败" }

export default function TeachingLessonsPage() {
  const [outlines, setOutlines] = useState<TeachingOutline[]>([])
  const [outlineId, setOutlineId] = useState("")
  const [lessonNumber, setLessonNumber] = useState(1)
  const [models, setModels] = useState<Model[]>([])
  const [modelId, setModelId] = useState("")
  const [history, setHistory] = useState<TeachingLesson[]>([])
  const [lesson, setLesson] = useState<TeachingLesson | null>(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")
  const outline = outlines.find(item => item.id === outlineId)
  const approved = outline?.currentRevisionId && outline.currentRevisionId === outline.confirmedRevisionId

  function show(item: TeachingLesson) {
    setLesson(item)
    setHistory(current => [item, ...current.filter(saved => saved.id !== item.id)])
    window.history.replaceState(null, "", `?lesson=${encodeURIComponent(item.id)}`)
  }
  useEffect(() => {
    let active = true
    const query = new URLSearchParams(window.location.search)
    setOutlineId(query.get("outline") || "")
    const id = query.get("lesson")
    Promise.allSettled([TeachingOutlineService.list(), getAllModels(), TeachingLessonService.list(), id ? TeachingLessonService.get(id) : Promise.resolve(null)] as const).then(results => {
      if (!active) return
      const failures: string[] = []
      try { if (results[0].status === "fulfilled") setOutlines(data(results[0].value)); else throw results[0].reason }
      catch (e) { failures.push(e instanceof Error ? e.message : "读取大纲失败") }
      try { if (results[1].status === "fulfilled") setModels(data(results[1].value).filter(model => model.status && model.type === "CHAT")); else throw results[1].reason }
      catch (e) { failures.push(e instanceof Error ? e.message : "读取模型失败") }
      try { if (results[2].status === "fulfilled") setHistory(data(results[2].value)); else throw results[2].reason }
      catch (e) { failures.push(e instanceof Error ? e.message : "读取课时历史失败") }
      try { if (results[3].status === "fulfilled" && results[3].value) show(data(results[3].value)); else if (results[3].status === "rejected") throw results[3].reason }
      catch (e) { failures.push(e instanceof Error ? e.message : "读取课时失败") }
      setError(failures.join("；")); setBusy(false)
    })
    return () => { active = false }
  }, [])

  async function open(id: string) {
    setBusy(true); setError(""); setNotice("")
    try { show(data(await TeachingLessonService.get(id))) }
    catch (e) { setError(e instanceof Error ? e.message : "读取材料失败") }
    finally { setBusy(false) }
  }
  async function generate(id: string) {
    setBusy(true); setError("")
    try {
      const generated = data(await TeachingLessonService.generate(id))
      show(generated)
      setNotice(generated.run.status === "SUCCEEDED" ? "双稿已保存为同一修订，请教师核查后使用。" : "运行状态与失败原因已保存。")
    } catch (e) { setError(e instanceof Error ? e.message : "生成请求中断；请刷新查看服务端状态") }
    finally { setBusy(false) }
  }
  async function create(event: React.FormEvent) {
    event.preventDefault()
    if (!outline?.confirmedRevisionId || !approved) return
    setBusy(true); setError(""); setNotice("")
    try {
      const saved = data(await TeachingLessonService.create({ outlineId, expectedOutlineRevisionId: outline.confirmedRevisionId, lessonNumber, modelId }))
      show(saved)
      await generate(saved.id)
    } catch (e) { setError(e instanceof Error ? e.message : "创建课时失败"); setBusy(false) }
  }

  return <main className="mx-auto w-full min-w-0 max-w-6xl space-y-6 px-4 py-8 md:px-8">
    <header className="space-y-2"><Link className="text-sm underline" href="/teaching/outlines">返回模块大纲</Link><h1 className="text-3xl font-semibold">课时讲义与教案</h1><p className="text-muted-foreground">从已确认大纲选择课时，生成共用知识、推导与例题的两份教学材料。</p></header>
    {error && <p role="alert" className="rounded-md border border-destructive p-3 text-destructive">{error}</p>}
    {notice && <p role="status" className="rounded-md border p-3">{notice}</p>}
    {busy && <p role="status" aria-live="polite">正在读取或生成，请稍候；刷新后可继续查看已保存的目标。</p>}
    <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(220px,1fr)]">
      <form onSubmit={create} className="space-y-4 rounded-lg border p-5"><h2 className="text-xl font-semibold">选择课时</h2><fieldset disabled={busy} className="space-y-4">
        <div className="space-y-2"><Label htmlFor="lesson-outline">已确认模块大纲</Label><select id="lesson-outline" className="h-10 w-full rounded-md border bg-background px-3" required value={outlineId} onChange={event => { setOutlineId(event.target.value); setLessonNumber(1) }}><option value="">请选择大纲</option>{outlines.map(item => <option key={item.id} value={item.id} disabled={!item.confirmedRevisionId || item.confirmedRevisionId !== item.currentRevisionId}>{item.parameters.module} · {item.parameters.totalLessons}课时{item.confirmedRevisionId !== item.currentRevisionId || !item.confirmedRevisionId ? "（请先确认）" : ""}</option>)}</select></div>
        <div className="space-y-2"><Label htmlFor="lesson-number">课时主题</Label><select id="lesson-number" required className="h-10 w-full rounded-md border bg-background px-3" value={lessonNumber} onChange={event => setLessonNumber(Number(event.target.value))}>{outline?.revision?.content.lessons.map((item, at) => <option key={at} value={at + 1}>第{at + 1}课时 · {item.title}</option>)}</select></div>
        <div className="space-y-2"><Label htmlFor="lesson-model">生成模型</Label><select id="lesson-model" className="h-10 w-full rounded-md border bg-background px-3" required value={modelId} onChange={event => setModelId(event.target.value)}><option value="">请选择已配置模型</option>{models.map(model => <option key={model.id} value={model.id}>{model.name}</option>)}</select>{!models.length && <p className="text-sm text-muted-foreground">请先到<Link className="underline" href="/settings/providers">模型设置</Link>配置可用对话模型。</p>}</div>
        {outline && <p className="text-sm text-muted-foreground">{outline.parameters.textbook} / {outline.parameters.edition} · {outline.parameters.level} · {outline.parameters.lessonMinutes}分钟 · {outline.parameters.studentFoundation}基础</p>}
        {!approved && <p className="text-sm text-muted-foreground">请先确认当前大纲修订，再生成课时材料。</p>}
        <Button type="submit" disabled={!approved || !modelId}>生成课时双稿</Button>
      </fieldset></form>
      <aside className="space-y-3 rounded-lg border p-5"><h2 className="text-lg font-semibold">已保存课时</h2>{!history.length && <p className="text-sm text-muted-foreground">生成目标将在这里保留，失败也可刷新查看。</p>}<ul className="space-y-2">{history.map(item => <li key={item.id}><button className="w-full rounded-md border p-3 text-left hover:bg-muted focus-visible:outline focus-visible:outline-2" disabled={busy} onClick={() => open(item.id)}><span className="block break-words font-medium">第{item.parameters.lessonNumber}课时 · {item.parameters.lessonTitle}</span><span className="text-sm text-muted-foreground">{runLabels[item.run.status]}</span></button></li>)}</ul></aside>
    </div>
    {lesson && <section aria-label="课时材料结果" className="min-w-0 space-y-4 rounded-lg border p-5">
      <p role="status">{runLabels[lesson.run.status]}</p>
      <p className="break-all text-xs text-muted-foreground">运行：{lesson.run.id} · 知识版本：{lesson.knowledgeVersionId} · 输入大纲修订：{lesson.inputRevisionId}</p>
      <p className="text-sm">{lesson.parameters.textbook} / {lesson.parameters.edition} · {lesson.parameters.level} · {lesson.parameters.lessonMinutes}分钟 · {lesson.parameters.studentFoundation}基础</p>
      {lesson.run.error && <p role="alert" className="text-destructive">{lesson.run.error}</p>}
      {lesson.run.status === "PENDING" && <Button disabled={busy} onClick={() => generate(lesson.id)}>继续生成双稿</Button>}
      <Button variant="outline" disabled={busy} onClick={() => open(lesson.id)}>刷新课时状态</Button>
      {lesson.run.status === "FAILED" && <p className="text-sm text-muted-foreground">请核对模型与来源后创建新目标，失败记录保留。</p>}
      {lesson.revision && <><p className="break-all text-xs text-muted-foreground" data-testid="lesson-revision">双稿共同修订：{lesson.revision.id}</p><LessonPreview key={lesson.revision.id} content={lesson.revision.content} /></>}
    </section>}
  </main>
}
