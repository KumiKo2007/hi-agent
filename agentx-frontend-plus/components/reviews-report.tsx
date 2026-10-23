import type { PrReviewContent, ReviewFinding } from "@/lib/pr-review-service"

function Findings({ title, findings }: { title: string; findings: ReviewFinding[] }) {
  return <section className="space-y-3" aria-label={title}>
    <h3 className="text-lg font-semibold">{title} · {findings.length}</h3>
    {!findings.length && <p className="rounded-md border border-dashed p-4 text-sm text-muted-foreground">本范围内没有此类发现；这不代表全仓无 Bug。</p>}
    {findings.map((finding, index) => <article key={index} className="space-y-3 rounded-md border p-4">
      <h4 className="font-medium"><span className="mr-2 rounded border px-2 py-0.5 font-mono text-xs">{finding.severity}</span>{finding.title}</h4>
      <p className="break-all font-mono text-xs">{finding.path} · {finding.side} · L{finding.startLine}–L{finding.endLine} @ {finding.commitSha}</p>
      <p className="whitespace-pre-wrap break-words text-sm">{finding.explanation}</p>
      {finding.verification === "SOURCE_VERIFIED" && finding.sourceUrl.startsWith("https://github.com/") && <a href={finding.sourceUrl} target="_blank" rel="noopener noreferrer" className="text-sm text-primary underline">核对固定提交源码</a>}
      <pre aria-label="源码引用" className="max-h-80 overflow-auto whitespace-pre-wrap break-all rounded-md bg-muted p-3 text-xs">{finding.evidence}</pre>
      <p className="text-sm text-muted-foreground">{finding.verificationNote}</p>
    </article>)}
  </section>
}

export function ReviewReport({ report }: { report: PrReviewContent }) {
  return <div className="min-w-0 space-y-6">
    <div className="space-y-2 rounded-md border bg-muted/30 p-4 text-sm">
      <p className="font-medium">{report.repository} #{report.pullNumber}</p>
      <p className="break-all font-mono text-xs">base {report.baseSha}</p>
      <p className="break-all font-mono text-xs">head {report.headSha}</p>
      <p>{report.assessmentNote}</p>
    </div>
    <p className="whitespace-pre-wrap break-words text-sm">{report.summary}</p>
    <Findings title="有源码证据的发现（结论待人工复核）" findings={report.findings} />
    <Findings title="待核实线索" findings={report.unverifiedFindings} />
    <section className="space-y-2"><h3 className="text-lg font-semibold">信息缺口与执行范围</h3>
      <p className="text-sm">未执行代码或测试，未向 GitHub 写入。</p>
      <ul className="list-disc space-y-2 pl-5 text-sm text-muted-foreground">{report.gaps.map((gap, index) => <li key={index} className="break-words">{gap}</li>)}</ul>
    </section>
  </div>
}
