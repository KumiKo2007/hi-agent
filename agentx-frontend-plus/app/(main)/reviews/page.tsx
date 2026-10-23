"use client"

import { useEffect, useState, type FormEvent } from "react"
import Link from "next/link"
import { GitPullRequest, Loader2, LockKeyhole } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { prInputService, type PrInputChoice, type PrInputSnapshot } from "@/lib/pr-input-service"
import type { GitHubConnection } from "@/lib/github-connection-service"

export default function ReviewsPage() {
  const [connection, setConnection] = useState<GitHubConnection | null>(null)
  const [choices, setChoices] = useState<PrInputChoice[]>([])
  const [choice, setChoice] = useState("")
  const [url, setUrl] = useState("")
  const [input, setInput] = useState<PrInputSnapshot | null>(null)
  const [loading, setLoading] = useState(true)
  const [reading, setReading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    async function load() {
      try {
        const options = await prInputService.options()
        if (!active) return
        if (options.code !== 200) throw new Error(options.message)
        setConnection(options.data.connection)
        setChoices(options.data.choices)
        setChoice(options.data.choices[0] ? "0" : "")
        const id = new URLSearchParams(window.location.search).get("input")
        if (id) {
          const saved = await prInputService.get(id)
          if (!active) return
          if (saved.code !== 200) throw new Error(saved.message)
          setInput(saved.data)
          setUrl(saved.data.pullRequestUrl)
        }
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : "读取配置失败")
      } finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false }
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const selected = choices[Number(choice)]
    if (!selected || !connection || reading) return
    setReading(true)
    setError(null)
    setInput(null)
    try {
      const response = await prInputService.create({ agentId: selected.agentId, serviceId: selected.serviceId, pullRequestUrl: url.trim() })
      if (response.code !== 200) throw new Error(response.message)
      setInput(response.data)
      window.history.replaceState(null, "", `/reviews?input=${encodeURIComponent(response.data.id)}`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "PR 读取失败，请检查个人连接和读取权限")
    } finally { setReading(false) }
  }

  return (
    <main className="container max-w-5xl space-y-6 px-4 py-8">
      <header className="space-y-2">
        <p className="flex items-center gap-2 text-sm text-muted-foreground"><GitPullRequest className="h-4 w-4" />代码审查</p>
        <h1 className="text-3xl font-semibold tracking-tight">先固定本次审查的输入</h1>
        <p className="text-muted-foreground">使用你的 GitHub 连接读取 PR，在确定的提交上查看差异、源码和测试。</p>
      </header>
      <Card>
        <CardHeader><CardTitle className="text-lg">选择连接与 PR</CardTitle></CardHeader>
        <CardContent>
          {loading ? <p role="status">正在读取个人连接…</p> : (
            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="github-connection">个人 GitHub 连接</Label>
                <select id="github-connection" className="w-full rounded-md border bg-background p-2" disabled={reading || !connection}>
                  <option>{connection ? `${connection.githubLogin} · ${connection.repositories.join("、")}` : "尚未连接 GitHub"}</option>
                </select>
                <Link href="/settings/github" className="text-sm text-primary underline">管理连接与仓库授权</Link>
              </div>
              <div className="space-y-2">
                <Label htmlFor="pr-reader">读取能力</Label>
                <select id="pr-reader" value={choice} onChange={event => setChoice(event.target.value)} disabled={reading || !choices.length} className="w-full rounded-md border bg-background p-2">
                  {!choices.length && <option value="">暂无可用读取能力</option>}
                  {choices.map((item, index) => <option value={index} key={`${item.agentId}:${item.serviceId}`}>{item.agentName} · {item.serviceId}</option>)}
                </select>
                {!choices.length && <p className="text-sm text-muted-foreground">请为已启用的 Agent 安装并允许 GitHub PR 和文件读取动作，平台需要明确将它们归类为只读。</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="pr-url">GitHub PR 链接</Label>
                <Input id="pr-url" type="url" required value={url} onChange={event => setUrl(event.target.value)} disabled={reading} placeholder="https://github.com/owner/repo/pull/42" />
              </div>
              <Button type="submit" disabled={!connection || !choices.length || reading}>{reading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}{reading ? "正在读取并固定提交…" : "读取并固定输入"}</Button>
            </form>
          )}
          {reading && <p role="status" className="mt-3 text-sm text-muted-foreground">正在核验授权、读取差异与固定提交的上下文，请稍候。</p>}
          {error && <p role="alert" aria-label="PR 输入错误" className="mt-4 rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive">{error}</p>}
        </CardContent>
      </Card>
      <Link href="/reviews/reports" className="inline-block text-sm text-primary underline">查看保存的审查报告</Link>
      {input && <section aria-label="固定 PR 输入" className="space-y-4">
        <Card><CardHeader><CardTitle className="text-xl">{input.title}</CardTitle><p className="text-sm text-muted-foreground">{input.repository} #{input.pullNumber} · {new Date(input.createdAt).toLocaleString("zh-CN")}</p></CardHeader>
          <CardContent className="space-y-4">
            <Link href={`/reviews/reports?input=${encodeURIComponent(input.id)}`} className="inline-block rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground">为此固定输入生成 Bug 报告</Link>
            <div className="flex items-center gap-2 text-sm"><LockKeyhole className="h-4 w-4" />已固定输入，刷新页面可恢复此快照。</div>
            <dl className="grid gap-3 rounded-lg bg-muted/50 p-4 text-sm">
              <div><dt className="text-muted-foreground">基准提交（base）</dt><dd className="break-all font-mono">{input.baseSha}</dd></div>
              <div><dt className="text-muted-foreground">目标提交（head）</dt><dd className="break-all font-mono">{input.headSha}</dd></div>
            </dl>
            <p className="whitespace-pre-wrap break-words text-sm">{input.body}</p>
          </CardContent>
        </Card>
        <Card><CardHeader><CardTitle className="text-lg">读取范围与信息缺口</CardTitle></CardHeader><CardContent><ul className="list-disc space-y-2 pl-5 text-sm text-muted-foreground">{input.gaps.map((gap, index) => <li key={index}>{gap}</li>)}</ul></CardContent></Card>
        <Card><CardHeader><CardTitle className="text-lg">变更文件 · {input.files.length}</CardTitle></CardHeader><CardContent className="space-y-3">{input.files.map(file => <details key={file.path} className="rounded-md border p-3"><summary className="cursor-pointer break-all text-sm font-medium">{file.path} · {file.status}</summary><pre className="mt-3 max-h-96 overflow-auto whitespace-pre-wrap break-all rounded bg-muted p-3 text-xs">{file.patch ?? "远端未提供文本差异"}</pre></details>)}</CardContent></Card>
        <Card><CardHeader><CardTitle className="text-lg">固定提交上下文 · {input.contexts.length}</CardTitle></CardHeader><CardContent className="space-y-3">{input.contexts.map(context => <details key={`${context.commitSha}:${context.path}`} className="rounded-md border p-3"><summary className="cursor-pointer break-all text-sm">{context.path} @ {context.commitSha.slice(0, 12)}</summary><pre className="mt-3 max-h-96 overflow-auto whitespace-pre-wrap break-all rounded bg-muted p-3 text-xs">{context.content}</pre></details>)}</CardContent></Card>
      </section>}
    </main>
  )
}
