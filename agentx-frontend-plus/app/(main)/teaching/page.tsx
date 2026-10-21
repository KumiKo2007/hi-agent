"use client"

import { useEffect, useRef, useState } from "react"
import Link from "next/link"
import { BookOpen, RefreshCw, Upload } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { getAllDatasets, getKnowledgeReadiness, createReadyKnowledgeVersion, getReadyKnowledgeVersion, uploadFile } from "@/lib/rag-dataset-service"
import { TeachingService } from "@/lib/teaching-service"
import type { KnowledgeReadiness, RagDataset, ReadyKnowledgePreview, ApiResponse } from "@/types/rag-dataset"
import type { TeachingRange, TeachingSelection, TeachingSource } from "@/types/teaching"

function value<T>(response: ApiResponse<T>): T {
  if (response.code !== 200) throw new Error(response.message || "操作失败，请重试")
  return response.data
}

function location(source: TeachingSource) {
  return source.locatorType === "FILE_PAGE" ? `文件物理第 ${source.physicalPage} 页` : `来源处理单元 ${source.physicalPage}（非物理页）`
}

export default function TeachingPage() {
  const [datasets, setDatasets] = useState<RagDataset[]>([])
  const [datasetId, setDatasetId] = useState("")
  const datasetRef = useRef("")
  const requestId = useRef(0)
  const [readiness, setReadiness] = useState<KnowledgeReadiness | null>(null)
  const [preview, setPreview] = useState<ReadyKnowledgePreview | null>(null)
  const [ranges, setRanges] = useState<TeachingRange[]>([])
  const [title, setTitle] = useState("高二数学 · 导数模块")
  const [history, setHistory] = useState<TeachingSelection[]>([])
  const [selection, setSelection] = useState<TeachingSelection | null>(null)
  const [source, setSource] = useState<TeachingSource | null>(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")

  useEffect(() => {
    let active = true
    const id = new URLSearchParams(window.location.search).get("selection")
    Promise.allSettled([getAllDatasets(), TeachingService.list(), id ? TeachingService.get(id) : Promise.resolve(null)] as const).then(results => {
      if (!active) return
      const errors: string[] = []
      try {
        const all = results[0]
        if (all.status === "fulfilled") setDatasets(value(all.value))
        else throw all.reason
      } catch (e) { errors.push(e instanceof Error ? e.message : "读取知识库失败") }
      try {
        const saved = results[1]
        if (saved.status === "fulfilled") setHistory(value(saved.value))
        else throw saved.reason
      } catch (e) { errors.push(e instanceof Error ? e.message : "读取选材历史失败") }
      try {
        const saved = results[2]
        if (saved.status === "fulfilled" && saved.value) setSelection(value(saved.value))
        else if (saved.status === "rejected") throw saved.reason
      } catch (e) { errors.push(e instanceof Error ? e.message : "读取固定选材失败") }
      setError(errors.join("；"))
      setBusy(false)
    })
    return () => { active = false; requestId.current = -1 }
  }, [])

  async function loadDataset(id: string) {
    datasetRef.current = id
    setDatasetId(id); setReadiness(null); setPreview(null); setRanges([]); setError(""); setNotice("")
    const token = ++requestId.current
    if (!id) return
    setBusy(true)
    try {
      const state = value(await getKnowledgeReadiness(id))
      if (token !== requestId.current) return
      setReadiness(state)
      if (state.latestReadyVersionId) {
        const fixed = value(await getReadyKnowledgeVersion(id, state.latestReadyVersionId))
        if (token === requestId.current) setPreview(fixed)
      }
    } catch (e) { if (token === requestId.current) setError(e instanceof Error ? e.message : "读取资料失败") }
    finally { if (token === requestId.current) setBusy(false) }
  }

  async function confirmVersion() {
    const id = datasetId
    setBusy(true); setError("")
    try {
      const version = value(await createReadyKnowledgeVersion(id))
      if (datasetRef.current === id) await loadDataset(id)
      setNotice(`已固定知识版本 ${version.version}，请选择实际使用的文件与页码。`)
    } catch (e) { setError(e instanceof Error ? e.message : "确认版本失败") }
    finally { setBusy(false) }
  }

  async function upload(file?: File) {
    if (!file) return
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) { setError("请选择 PDF 文件"); return }
    const id = datasetId
    setBusy(true); setError("")
    try {
      value(await uploadFile(id, file))
      if (datasetRef.current === id) await loadDataset(id)
      setNotice("PDF 已上传，请刷新处理状态。处理完成后确认新版本，才能选择新文件。")
    } catch (e) { setError(e instanceof Error ? e.message : "上传失败") }
    finally { setBusy(false) }
  }

  function changeRange(fileId: string, patch: Partial<TeachingRange>) {
    setRanges(current => current.map(range => range.fileId === fileId ? { ...range, ...patch } : range))
  }

  async function save() {
    if (!preview) return
    setBusy(true); setError("")
    try {
      const saved = value(await TeachingService.save({ title, datasetId, versionId: preview.id, ranges }))
      setSelection(saved); setSource(null)
      setHistory(current => [saved, ...current])
      window.history.replaceState(null, "", `?selection=${encodeURIComponent(saved.id)}`)
      setNotice("选材已保存，刷新后可继续读取相同版本和来源。")
    } catch (e) { setError(e instanceof Error ? e.message : "保存选材失败") }
    finally { setBusy(false) }
  }

  async function openSaved(id: string) {
    setBusy(true); setError("")
    try {
      setSelection(value(await TeachingService.get(id))); setSource(null)
      window.history.replaceState(null, "", `?selection=${encodeURIComponent(id)}`)
    } catch (e) { setError(e instanceof Error ? e.message : "读取选材失败") }
    finally { setBusy(false) }
  }

  async function openSource(id: string) {
    if (!selection || busy) return
    setBusy(true); setError("")
    try { setSource(value(await TeachingService.source(selection.id, id))) }
    catch (e) { setError(e instanceof Error ? e.message : "读取来源失败") }
    finally { setBusy(false) }
  }

  return <main className="mx-auto w-full max-w-6xl space-y-6 px-4 py-8 md:px-8">
    <header className="space-y-2"><div className="flex items-center gap-2 text-muted-foreground"><BookOpen className="h-5 w-5" aria-hidden />教学准备</div>
      <h1 className="text-3xl font-semibold tracking-tight">确定本次教学的资料</h1>
      <p className="max-w-2xl text-muted-foreground">先核对来源，再保存明确的章节范围。后续知识库更新不会改变已保存选材。</p></header>
    {error && <p role="alert" className="rounded-md border border-destructive p-3 text-destructive">{error}</p>}
    {notice && <p role="status" className="rounded-md border p-3">{notice}</p>}
    <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(240px,1fr)]">
      <div className="space-y-6"><Card><CardHeader><CardTitle>1. 选择知识库与版本</CardTitle></CardHeader><CardContent className="space-y-4">
        <Label htmlFor="teaching-dataset">知识库</Label>
        <select id="teaching-dataset" className="h-10 w-full rounded-md border bg-background px-3" value={datasetId} onChange={event => loadDataset(event.target.value)} disabled={busy}>
          <option value="">请选择你的知识库</option>{datasets.map(dataset => <option key={dataset.id} value={dataset.id}>{dataset.name}</option>)}
        </select>
        <div className="flex flex-wrap items-center gap-3"><Button variant="outline" disabled={!datasetId || busy} onClick={() => loadDataset(datasetId)}><RefreshCw className="mr-2 h-4 w-4" aria-hidden />刷新处理状态</Button>
          <Label className="flex items-center gap-2" htmlFor="teaching-pdf"><Upload className="h-4 w-4" aria-hidden />上传 PDF</Label>
          <input id="teaching-pdf" aria-label="上传 PDF" className="max-w-full text-sm" type="file" accept=".pdf,application/pdf" disabled={!datasetId || busy} onChange={event => { void upload(event.target.files?.[0]); event.target.value = "" }} /></div>
        <Link className="text-sm underline underline-offset-4" href="/knowledge">管理知识库或新建知识库</Link>
        {readiness && <div className="space-y-2 rounded-md border p-3" aria-live="polite">
          <p>实际处理状态：{({ EMPTY: "尚无资料", PROCESSING: "处理中", FAILED: "处理失败", READY: "全部处理完成" })[readiness.status]} · {readiness.readyFileCount}/{readiness.fileCount} 个文件就绪</p>
          {readiness.failureReason && <p className="text-destructive">{readiness.failureReason}</p>}
          {readiness.status !== "READY" && <p className="text-sm text-muted-foreground">请在知识库核对失败或未完成文件，再刷新状态。已有固定版本仍可选用，但不包含新上传资料。</p>}
          <Button disabled={busy || readiness.status !== "READY"} onClick={confirmVersion}>确认当前资料为新版本</Button>
        </div>}
        {preview && <p className="break-all text-sm">本次选用固定知识版本：{preview.version} · {preview.id}</p>}
      </CardContent></Card>
      {preview && <Card><CardHeader><CardTitle>2. 限定文件与章节范围</CardTitle></CardHeader><CardContent className="space-y-5">
        <div className="space-y-2"><Label htmlFor="selection-title">选材名称</Label><Input id="selection-title" maxLength={120} value={title} onChange={event => setTitle(event.target.value)} /></div>
        <p className="text-sm text-muted-foreground">页码从 1 开始，指 PDF 文件物理页，不是课本印刷页。章节名用于标记，实际只采用选定文件页范围内的内容。</p>
        {preview.files.map(file => {
          const range = ranges.find(item => item.fileId === file.id)
          const pdf = file.fileName.toLowerCase().endsWith(".pdf")
          const last = Math.max(1, ...preview.sampleDocuments.filter(doc => doc.ragVersionFileId === file.id).map(doc => doc.page + 1))
          return <fieldset className="space-y-3 rounded-md border p-4" key={file.id}>
            <legend className="px-1"><label className="flex items-center gap-2"><input type="checkbox" checked={Boolean(range)} onChange={event => setRanges(current => event.target.checked ? [...current, { fileId: file.id, startPage: 1, endPage: last, chapter: "" }] : current.filter(item => item.fileId !== file.id))} />{file.fileName}{!pdf && "（按来源处理单元选择，需核查）"}</label></legend>
            {range && <div className="grid gap-3 sm:grid-cols-3"><div className="space-y-2"><Label htmlFor={`start-${file.id}`}>{pdf ? "开始物理页" : "开始来源单元"}</Label><Input id={`start-${file.id}`} type="number" min={1} value={range.startPage} onChange={event => changeRange(file.id, { startPage: Number(event.target.value) })} /></div>
              <div className="space-y-2"><Label htmlFor={`end-${file.id}`}>{pdf ? "结束物理页" : "结束来源单元"}</Label><Input id={`end-${file.id}`} type="number" min={1} value={range.endPage} onChange={event => changeRange(file.id, { endPage: Number(event.target.value) })} /></div>
              <div className="space-y-2"><Label htmlFor={`chapter-${file.id}`}>章节标记</Label><Input id={`chapter-${file.id}`} maxLength={200} value={range.chapter} onChange={event => changeRange(file.id, { chapter: event.target.value })} /></div></div>}
          </fieldset>
        })}
        <Button disabled={busy || !ranges.length || !title.trim() || ranges.some(range => range.startPage < 1 || range.endPage < range.startPage)} onClick={save}>{busy ? "正在处理…" : "保存固定选材"}</Button>
      </CardContent></Card>}
      </div>
      <aside><Card><CardHeader><CardTitle>已保存选材</CardTitle></CardHeader><CardContent><ul className="space-y-3">
        {!history.length && <li className="text-sm text-muted-foreground">保存后可在这里重读固定来源。</li>}
        {history.map(item => <li key={item.id}><button type="button" className="w-full rounded-md border p-3 text-left hover:bg-muted focus-visible:outline focus-visible:outline-2" disabled={busy} onClick={() => openSaved(item.id)}><span className="block font-medium">{item.title}</span><span className="text-xs text-muted-foreground">版本 {item.versionNumber} · {item.status === "READY" ? "来源齐备" : "来源待核查"}</span></button></li>)}
      </ul></CardContent></Card></aside>
    </div>
    {selection && <Card><CardHeader><CardTitle>固定来源：{selection.title}</CardTitle></CardHeader><CardContent className="space-y-4">
      <p className="break-all text-sm">知识版本 {selection.versionNumber} · {selection.versionId} · 选材 {selection.id}</p>
      <p role="status">{selection.status === "READY" ? "所选页均有可定位来源，内容仍需教师核查。" : "来源不完整，请核对缺页或缩小范围后重新保存。"}</p>
      {selection.warnings.length > 0 && <ul className="list-inside list-disc text-destructive">{selection.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>}
      <ul className="grid gap-2 sm:grid-cols-2">{selection.sources.map(item => <li key={item.id}><Button variant="outline" className="h-auto w-full justify-start whitespace-normal text-left" disabled={busy} onClick={() => openSource(item.id)}>{item.fileName} · {location(item)}</Button></li>)}</ul>
      {source && <section aria-label="来源原文" className="space-y-2 rounded-md bg-muted p-4"><h3 className="font-medium">{source.fileName} · {location(source)}</h3><p className="whitespace-pre-wrap break-words">{source.content}</p></section>}
    </CardContent></Card>}
  </main>
}
