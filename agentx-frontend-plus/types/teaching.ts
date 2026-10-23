export interface TeachingRange {
  fileId: string
  startPage: number
  endPage: number
  chapter: string
}
export interface TeachingSource {
  id: string
  fileId: string
  fileName: string
  physicalPage: number | null
  locatorType: "FILE_PAGE" | "DOCUMENT_UNIT" | "WORD_PARAGRAPH"
  paragraphIndex?: number | null
  heading?: string | null
  coverageWarnings?: string[]
  content: string
  sourceUrl: string
}
export interface TeachingSelection {
  id: string
  title: string
  datasetId: string
  versionId: string
  versionNumber: string
  ranges: TeachingRange[]
  sources: TeachingSource[]
  status: "READY" | "INCOMPLETE"
  warnings: string[]
  createdAt: string
}
export interface SaveTeachingSelection {
  title: string
  datasetId: string
  versionId: string
  ranges: TeachingRange[]
}
