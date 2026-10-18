"use client"

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { toast } from "@/hooks/use-toast"
import { githubConnectionService, type GitHubConnection } from "@/lib/github-connection-service"

export default function GitHubConnectionPage() {
  const [connection, setConnection] = useState<GitHubConnection | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [replacing, setReplacing] = useState(false)
  const [token, setToken] = useState("")
  const [repositoryText, setRepositoryText] = useState("")
  const [selected, setSelected] = useState<string[]>([])

  useEffect(() => {
    githubConnectionService.get().then((response) => {
      if (response.code === 200) {
        setConnection(response.data)
        setSelected(response.data?.repositories ?? [])
      } else {
        toast({ title: "读取 GitHub 连接失败", description: response.message, variant: "destructive" })
      }
    }).catch(() => {
      toast({ title: "读取 GitHub 连接失败", variant: "destructive" })
    }).finally(() => setLoading(false))
  }, [])

  const submitConnection = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const repositories = repositoryText.split(/[\s,]+/).map((value) => value.trim()).filter(Boolean)
    if (!token.trim() || repositories.length === 0) {
      toast({ title: "请输入凭据并指定至少一个仓库", variant: "destructive" })
      return
    }
    setBusy(true)
    try {
      const response = await githubConnectionService.connect(token.trim(), repositories)
      if (response.code !== 200) throw new Error(response.message)
      setConnection(response.data)
      setSelected(response.data.repositories)
      setReplacing(false)
      setToken("")
      setRepositoryText("")
      toast({ title: "GitHub 连接已保存" })
    } catch (error) {
      toast({ title: "连接失败", description: error instanceof Error ? error.message : "请稍后重试", variant: "destructive" })
    } finally {
      setBusy(false)
    }
  }

  const narrow = async () => {
    if (selected.length === 0) {
      toast({ title: "至少保留一个仓库；如需完全断开，请撤销连接", variant: "destructive" })
      return
    }
    setBusy(true)
    try {
      const response = await githubConnectionService.narrowRepositories(selected)
      if (response.code !== 200) throw new Error(response.message)
      setConnection(response.data)
      setSelected(response.data.repositories)
      toast({ title: "仓库授权范围已收紧" })
    } catch (error) {
      toast({ title: "保存失败", description: error instanceof Error ? error.message : "请稍后重试", variant: "destructive" })
    } finally {
      setBusy(false)
    }
  }

  const revoke = async () => {
    if (!window.confirm("撤销本平台的 GitHub 连接？等待中的写入将无法使用此连接。")) return
    setBusy(true)
    try {
      const response = await githubConnectionService.revoke()
      if (response.code !== 200) throw new Error(response.message)
      setConnection(null)
      setSelected([])
      setReplacing(false)
      setToken("")
      toast({ title: "GitHub 连接已撤销" })
    } catch (error) {
      toast({ title: "撤销失败", description: error instanceof Error ? error.message : "请稍后重试", variant: "destructive" })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container max-w-3xl space-y-6 py-6">
      <div>
        <h1 className="text-2xl font-semibold">GitHub 连接</h1>
        <p className="mt-1 text-sm text-muted-foreground">使用你自己的 GitHub 身份，并限定 Agent 可写入的仓库。</p>
      </div>

      {loading ? <p role="status">正在读取连接…</p> : connection && !replacing ? (
        <Card>
          <CardHeader>
            <CardTitle>已连接：{connection.githubLogin}</CardTitle>
            <CardDescription>身份编号 {connection.githubUserId}。以下仓库是本平台允许使用的范围。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <fieldset className="space-y-3">
              <legend className="font-medium">授权仓库</legend>
              {connection.repositories.map((repository) => (
                <div key={repository} className="flex items-center gap-3">
                  <Checkbox id={repository} checked={selected.includes(repository)} disabled={busy}
                    onCheckedChange={(checked) => setSelected((current) => checked
                      ? [...current, repository]
                      : current.filter((item) => item !== repository))} />
                  <Label htmlFor={repository}>{repository}</Label>
                </div>
              ))}
            </fieldset>
            <div className="flex flex-wrap gap-3">
              <Button onClick={narrow} disabled={busy || selected.length === connection.repositories.length}>缩小授权范围</Button>
              <Button variant="outline" onClick={() => setReplacing(true)} disabled={busy}>重新连接并选择仓库</Button>
              <Button variant="destructive" onClick={revoke} disabled={busy}>撤销连接</Button>
            </div>
            <p className="text-sm text-muted-foreground">撤销会立即删除本平台保存的凭据。如需在 GitHub 端撤销令牌，还需前往 GitHub 的令牌设置页面。</p>
          </CardContent>
        </Card>
      ) : null}

      {!loading && (!connection || replacing) && (
        <Card>
          <CardHeader>
            <CardTitle>{connection ? "重新连接" : "连接 GitHub"}</CardTitle>
            <CardDescription>
              在 GitHub 创建 fine-grained personal access token，选择需要的仓库，并授予 Issues：Read and write。
              本平台会再按下面的仓库名单收紧范围。令牌只提交一次，之后不在页面显示。
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <a className="text-sm underline" href="https://github.com/settings/personal-access-tokens/new" target="_blank" rel="noopener noreferrer">打开 GitHub 令牌创建页面</a>
            <form className="space-y-4" onSubmit={submitConnection}>
              <div className="space-y-2">
                <Label htmlFor="github-token">Fine-grained personal access token</Label>
                <Input id="github-token" type="password" value={token} onChange={(event) => setToken(event.target.value)} autoComplete="off" required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="github-repositories">允许的仓库</Label>
                <Input id="github-repositories" value={repositoryText} onChange={(event) => setRepositoryText(event.target.value)} placeholder="owner/repo, team/another-repo" required />
                <p className="text-xs text-muted-foreground">用逗号或空格分隔。必须属于该令牌可访问的仓库。</p>
              </div>
              <div className="flex gap-3">
                <Button type="submit" disabled={busy}>验证并保存连接</Button>
                {connection && <Button type="button" variant="outline" onClick={() => { setReplacing(false); setToken("") }} disabled={busy}>取消</Button>}
              </div>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
