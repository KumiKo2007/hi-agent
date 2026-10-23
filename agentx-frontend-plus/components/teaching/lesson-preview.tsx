"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { LessonMath } from "@/components/teaching/lesson-math"
import type { LessonBlock, LessonContent, LessonSource } from "@/types/teaching-lesson"

const evidenceLabels = { SOURCE: "来源依据（需核对）", SUPPLEMENT: "补充 / 自编", UNSUPPORTED: "证据不足，待核查" }
function location(source: LessonSource) {
  if (source.locatorType === "WORD_PARAGRAPH") return `原文段落 ${source.paragraphIndex ?? "未知"}${source.heading ? ` · ${source.heading}` : ""}`
  return source.locatorType === "FILE_PAGE" && source.physicalPage != null ? `文件物理页 ${source.physicalPage}` : "定位不完整，待核查"
}
function Evidence({ block, sources }: { block: LessonBlock; sources: LessonSource[] }) {
  return <div className="space-y-1 border-t pt-2 text-sm text-muted-foreground">
    <p>{evidenceLabels[block.evidence.kind]}{block.evidence.note && `：${block.evidence.note}`}</p>
    {block.evidence.sourceIds.map(id => {
      const source = sources.find(source => source.id === id)
      return source ? <a className="mr-3 inline-block underline" key={id} href={`#source-${encodeURIComponent(id)}`} onClick={() => {
        const detail = document.getElementById(`source-${encodeURIComponent(id)}`)
        if (detail instanceof HTMLDetailsElement) detail.open = true
      }}>{source.fileName} · {location(source)}</a>
        : <span key={id}>来源缺失，待核查</span>
    })}
  </div>
}
function SharedContent({ content }: { content: LessonContent }) {
  return <>{([['knowledge', '知识梳理'], ['derivations', '关键推导'], ['examples', '例题']] as const).map(([key, title]) => <section className="space-y-3" key={key}>
    <h3 className="text-lg font-semibold">{title}</h3>
    {content[key].map(block => <article className="space-y-3 rounded-md border p-4" key={block.id}>
      <h4 className="font-medium">{block.title}</h4><LessonMath>{block.text}</LessonMath>
      {key === "examples" && "solution" in block && <><h5 className="font-medium">例题解析</h5><LessonMath>{String(block.solution)}</LessonMath></>}
      <Evidence block={block} sources={content.sources} />
    </article>)}
  </section>)}</>
}
function TextList({ items }: { items: string[] }) {
  return <ul className="list-disc space-y-2 pl-5">{items.map((item, at) => <li key={at}><LessonMath>{item}</LessonMath></li>)}</ul>
}
export function LessonPreview({ content }: { content: LessonContent }) {
  const [audience, setAudience] = useState<"student" | "teacher">("student")
  return <div className="min-w-0 space-y-6">
    <div className="flex flex-wrap gap-2" role="group" aria-label="双稿视图">
      <Button aria-pressed={audience === "student"} variant={audience === "student" ? "default" : "outline"} onClick={() => setAudience("student")}>学生讲义</Button>
      <Button aria-pressed={audience === "teacher"} variant={audience === "teacher" ? "default" : "outline"} onClick={() => setAudience("teacher")}>教师教案</Button>
    </div>
    <section aria-label={audience === "student" ? "学生讲义预览" : "教师教案预览"} className="min-w-0 space-y-6">
      <h2 className="text-2xl font-semibold">{content.title} · {audience === "student" ? "学生讲义" : "教师教案"}</h2>
      <section className="space-y-3"><h3 className="text-lg font-semibold">{audience === "student" ? "学习目标" : "教学目标"}</h3><TextList items={content[audience].objectives} /></section>
      {audience === "teacher" && <section className="space-y-3"><h3 className="text-lg font-semibold">重难点</h3><TextList items={content.teacher.difficulties} /></section>}
      <SharedContent content={content} />
      {audience === "teacher" && <>
        <section className="space-y-3"><h3 className="text-lg font-semibold">教学步骤与时间分配</h3><ol className="space-y-4">{content.teacher.stages.map((stage, at) => <li className="border-l-2 pl-4" key={at}><h4 className="font-medium">{at + 1}. {stage.title} · {stage.minutes} 分钟</h4><LessonMath>{stage.guidance}</LessonMath></li>)}</ol></section>
        <section className="space-y-3"><h3 className="text-lg font-semibold">讲解提示</h3><TextList items={content.teacher.prompts} /></section>
        <section className="space-y-3"><h3 className="text-lg font-semibold">常见误区</h3><TextList items={content.teacher.misconceptions} /></section>
      </>}
    </section>
    <section className="space-y-3 border-t pt-5" aria-label="固定来源"><h2 className="text-xl font-semibold">来源与核查</h2>
      <p className="text-sm text-muted-foreground">以下引用来自本修订固定选材。引用位置可追溯不代表内容已由教师复核，使用前请核对数学与教学质量；公式解析失败会明确显示。</p>
      {!content.sources.length && <p>本材料未提供可查来源，全部依据须补充核查。</p>}
      {content.sources.map(source => <details className="rounded-md border p-3" key={source.id} id={`source-${encodeURIComponent(source.id)}`}>
        <summary className="cursor-pointer break-words font-medium">{source.fileName} · {location(source)}</summary>
        <p className="mt-3 whitespace-pre-wrap break-words text-sm">{source.content}</p>
        {source.coverageWarnings?.map((warning, at) => <p className="mt-2 text-sm text-destructive" key={at}>{warning}</p>)}
      </details>)}
    </section>
  </div>
}
