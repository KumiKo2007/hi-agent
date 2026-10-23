"use client"

import Image from "next/image"
import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { TeachingOcrService, type OcrFile, type OcrPage } from "@/lib/teaching-ocr-service"
import type { ApiResponse } from "@/types/rag-dataset"

interface Props {
  fileId: string
  versionId?: string
  onSelectPage?: (page: number) => void
  onReviewed?: () => void
}
function value<T>(response: ApiResponse<T>): T {
  if (response.code !== 200) throw new Error(response.message || "读取识别结果失败")
  return response.data
}

/** 原文当普通文本显示，不执行资料中的指令或HTML。 */
export function OcrReviewPanel({ fileId, versionId, onSelectPage, onReviewed }: Props) {
  const [file, setFile] = useState<OcrFile | null>(null)
  const [page, setPage] = useState<OcrPage | null>(null)
  const [image, setImage] = useState("")
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const generation = useRef(0)

  useEffect(() => {
    let active = true
    const id = ++generation.current
    setFile(null); setPage(null); setImage(""); setError(""); setBusy(true)
    TeachingOcrService.get(fileId, versionId).then(response => {
      if (active && id === generation.current) setFile(value(response))
    }).catch(e => { if (active && id === generation.current) setError(e instanceof Error ? e.message : "读取失败") })
      .finally(() => { if (id === generation.current) setBusy(false) })
    return () => { active = false }
  }, [fileId, versionId])

  async function open(source: OcrPage) {
    const id = ++generation.current
    setPage(source); setImage(""); setNote(source.review?.note || ""); setError(""); setBusy(true)
    try {
      const data = value(await TeachingOcrService.image(fileId, source.physicalPage, versionId))
      if (id === generation.current && data.startsWith("data:image/png;base64,")) setImage(data)
    } catch (e) { if (id === generation.current) setError(e instanceof Error ? e.message : "原页加载失败") }
    finally { if (id === generation.current) setBusy(false) }
  }
  async function confirm() {
    if (!versionId || !page || !image) return
    const id = generation.current
    setBusy(true); setError("")
    try {
      const saved = value(await TeachingOcrService.review(versionId, fileId, page.physicalPage, note))
      if (id === generation.current) {
        setPage(saved)
        onReviewed?.()
        setFile(current => current ? { ...current, pages: current.pages.map(item => item.physicalPage === saved.physicalPage ? saved : item) } : current)
      }
    } catch (e) { if (id === generation.current) setError(e instanceof Error ? e.message : "保存核对失败") }
    finally { if (id === generation.current) setBusy(false) }
  }

  return <section className="space-y-3 rounded-md border bg-muted/20 p-3" aria-label="逐页核对扫描PDF">
    <h3 className="font-medium">识别结果与原页核对</h3>
    <p className="text-sm text-muted-foreground">自动检查只能发现空结果、缺页及部分公式格式问题，不能保证数学正确。请逐项对照原页；缺失或错误内容请替换资料，或缩小选材范围。人工确认不会删除原警告。</p>
    {!versionId && <p className="text-sm">这是当前处理结果，刷新处理状态后重新打开可更新。确认知识版本后，才能保存该版本的人工核对记录。</p>}
    {busy && <p role="status">正在读取页面…</p>}
    {error && <p role="alert" className="text-destructive">{error}</p>}
    {file && <><p className="text-sm">{file.fileName} · {file.pageCount} 个物理页{file.processingError && ` · ${file.processingError}`}</p>
      {file.pageCount === 0 && <p role="status">尚未取得页数，请等待处理或检查文件失败原因。</p>}
      <div className="flex max-h-40 flex-wrap gap-2 overflow-auto">{file.pages.map(item => <Button key={item.physicalPage} size="sm" variant={page?.physicalPage === item.physicalPage ? "default" : "outline"} disabled={busy} onClick={() => open(item)}>
        第 {item.physicalPage} 页 · {({ MISSING: "缺口", FORMULA_GAP: "公式缺口", REVIEW_REQUIRED: "待人工核对", HUMAN_REVIEWED: "已人工核对" })[item.status]}
      </Button>)}</div></>}
    {page && <div className="space-y-3"><h4 className="font-medium">文件物理第 {page.physicalPage} 页（不是印刷页码）</h4>
      {page.coverageWarnings.length > 0 && <ul className="list-inside list-disc text-sm text-amber-800 dark:text-amber-300">{page.coverageWarnings.map((warning, index) => <li key={index}>自动检查：{warning}</li>)}</ul>}
      <div className="grid gap-3 md:grid-cols-2"><div className="max-h-[36rem] overflow-auto rounded-md border bg-white">{image ? <Image unoptimized width={1600} height={2000} src={image} alt={`文件物理第 ${page.physicalPage} 页原页`} className="h-auto w-full" /> : <p className="p-3 text-black">原页尚未加载成功。</p>}</div>
        <div><p className="mb-2 text-sm font-medium">识别文本</p><pre className="max-h-[36rem] overflow-auto whitespace-pre-wrap break-words rounded-md border p-3 text-sm">{page.content || "此页没有可用识别文本，不能人工确认绕过。"}</pre></div></div>
      {page.review && <p role="status" className="text-sm">人工核对：{page.review.note} · {new Date(page.review.reviewedAt).toLocaleString()}。这不是自动数学校验结论。</p>}
      {versionId && page.status !== "MISSING" && page.status !== "FORMULA_GAP" && <div className="space-y-2"><Label htmlFor={`ocr-note-${fileId}`}>原页核对依据（如公式、符号与原文是否一致）</Label>
        <textarea id={`ocr-note-${fileId}`} className="min-h-20 w-full rounded-md border bg-background p-2" maxLength={2000} value={note} onChange={event => setNote(event.target.value)} />
        <Button disabled={busy || !note.trim() || !image || Boolean(page.review)} onClick={confirm}>保存人工核对记录</Button></div>}
      {onSelectPage && <Button variant="outline" disabled={busy || (page.status === "MISSING" || page.status === "FORMULA_GAP")} onClick={() => onSelectPage(page.physicalPage)}>选材范围仅保留此页</Button>}
    </div>}
  </section>
}
