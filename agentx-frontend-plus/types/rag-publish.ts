// RAG发布相关类型定义

/** RAG版本发布状态 */
export enum RagPublishStatus {
  REVIEWING = 1,   // 审核中
  PUBLISHED = 2,   // 已发布  
  REJECTED = 3,    // 拒绝
  REMOVED = 4      // 已下架
}

/** RAG版本发布状态描述映射 */
export const RagPublishStatusText: Record<RagPublishStatus, string> = {
  [RagPublishStatus.REVIEWING]: "审核中",
  [RagPublishStatus.PUBLISHED]: "已发布", 
  [RagPublishStatus.REJECTED]: "拒绝",
  [RagPublishStatus.REMOVED]: "已下架"
}

/** RAG版本DTO */
export interface RagVersionDTO {
  id: string
  name: string
  icon?: string
  description?: string
  userId: string
  userNickname?: string
  version: string
  changeLog?: string
  labels: string[]
  originalRagId: string
  originalRagName?: string
  fileCount: number
  totalSize: number
  documentCount: number
  publishStatus: RagPublishStatus
  publishStatusDesc: string
  rejectReason?: string
  reviewTime?: string
  publishedAt?: string
  createdAt: string
  updatedAt: string
  installCount: number
  isInstalled?: boolean
}

/** 用户安装的RAG DTO */
export interface UserRagDTO {
  id: string
  userId: string
  ragVersionId: string
  name: string
  description?: string
  icon?: string
  version: string
  installedAt: string
  createdAt: string
  updatedAt: string
  originalRagId?: string
  fileCount?: number
  documentCount?: number
  creatorNickname?: string
  creatorId?: string
}

/** RAG市场DTO */
export interface RagMarketDTO {
  id: string
  name: string
  icon?: string
  description?: string
  version: string
  labels: string[]
  userId: string
  userNickname?: string
  userAvatar?: string
  fileCount: number
  documentCount: number
  totalSize: number
  totalSizeDisplay: string
  installCount: number
  publishedAt: string
  updatedAt: string
  isInstalled?: boolean
  rating?: number
  reviewCount?: number
}

/** 发布RAG请求 */
export interface PublishRagRequest {
  ragId: string
  version: string
  changeLog?: string
  labels?: string[]
}

/** 安装RAG请求 */
export interface InstallRagRequest {
  ragVersionId: string
}

/** 审核RAG版本请求 */
export interface ReviewRagVersionRequest {
  status: RagPublishStatus.PUBLISHED | RagPublishStatus.REJECTED
  rejectReason?: string
}

/** 分页响应 */
export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** API响应 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 查询参数 */
export interface QueryParams {
  page?: number
  pageSize?: number
  keyword?: string
}

/** RAG标签颜色映射 */
export const RAG_LABEL_COLORS = [
  "bg-primary/15 text-primary/90",
  "bg-green-500/15 text-green-400", 
  "bg-yellow-500/15 text-yellow-400",
  "bg-purple-500/15 text-purple-400",
  "bg-pink-500/15 text-pink-400",
  "bg-indigo-500/15 text-indigo-400"
]

/** 获取标签颜色 */
export const getLabelColor = (index: number): string => {
  return RAG_LABEL_COLORS[index % RAG_LABEL_COLORS.length]
}

/** 格式化文件大小 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return "0 B"
  
  const k = 1024
  const sizes = ["B", "KB", "MB", "GB", "TB"]
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i]
}

/** 格式化时间 */
export const formatDateTime = (dateString: string): string => {
  return new Date(dateString).toLocaleString('zh-CN')
}

/** 获取发布状态颜色 */
export const getPublishStatusColor = (status: RagPublishStatus): string => {
  switch (status) {
    case RagPublishStatus.REVIEWING:
      return "bg-yellow-500/15 text-yellow-400 border-yellow-500/40"
    case RagPublishStatus.PUBLISHED:
      return "bg-green-500/15 text-green-400 border-green-500/40"
    case RagPublishStatus.REJECTED:
      return "bg-red-500/15 text-red-400 border-red-500/40"
    case RagPublishStatus.REMOVED:
      return "bg-muted text-foreground border-border"
    default:
      return "bg-muted text-foreground border-border"
  }
}