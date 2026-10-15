"use client"

import { Button } from "@/components/ui/button"
import type { KnowledgeReadiness } from "@/types/rag-dataset"

interface KnowledgeReadinessPanelProps {
  readiness: KnowledgeReadiness | null
  creating: boolean
  onCreateVersion: () => void
  onOpenVersion: (versionId: string) => void
}

export function KnowledgeReadinessPanel({ readiness, creating, onCreateVersion, onOpenVersion }: KnowledgeReadinessPanelProps) {
  if (!readiness) {
    return <p className="text-sm text-muted-foreground">正在检查资料处理状态...</p>
  }

  const message = {
    EMPTY: "尚未上传资料，当前没有可检索内容。",
    PROCESSING: `资料处理中：${readiness.readyFileCount}/${readiness.fileCount} 个文件就绪。`,
    FAILED: `资料处理失败：${readiness.failedFileCount} 个文件需要处理。`,
    READY: `全部 ${readiness.fileCount} 个文件已完成索引。`,
  }[readiness.status]

  return <div className="space-y-2" aria-live="polite">
    <p className="text-sm">{message}</p>
    {readiness.failureReason && <p role="alert" className="text-sm text-destructive">{readiness.failureReason}</p>}
    {readiness.latestReadyVersionId && <div className="text-xs text-muted-foreground break-all">
      <span>可检索版本 ID：{readiness.latestReadyVersionId}</span>
      <span className="ml-2">({readiness.latestReadyVersionNumber})</span>
      <Button className="ml-2" size="sm" variant="link" aria-label="查看可检索版本"
        onClick={() => onOpenVersion(readiness.latestReadyVersionId!)}>查看快照</Button>
    </div>}
    {readiness.status === "READY" && <Button size="sm" variant="outline" disabled={creating}
      onClick={onCreateVersion}>{creating ? "正在确认版本..." : "确认可检索版本"}</Button>}
  </div>
}
