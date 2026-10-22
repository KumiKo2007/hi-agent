export interface OutlineLesson { title: string; objectives: string[] }
export interface OutlineParameters {
  subject: string; level: string; textbook: string; edition: string; module: string
  lessonMinutes: number; studentFoundation: string; totalLessons: number; scopeConfirmed: boolean
}
export interface OutlineRevision {
  id: string; artifactId: string; runId: string; parentRevisionId: string | null
  inputRevisionId: string | null; knowledgeVersionId: string
  content: { lessons: OutlineLesson[] }; createdAt: string
}
export interface TeachingOutline {
  id: string; kind: "TEACHING_OUTLINE"; selectionId: string; knowledgeVersionId: string
  parameters: OutlineParameters; status: "PENDING" | "DRAFT" | "CONFIRMED"
  currentRevisionId: string | null; confirmedRevisionId: string | null
  run: { id: string; modelId: string; connectionType: string; status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED"; error: string | null }
  revision: OutlineRevision | null
}
export interface CreateTeachingOutline extends OutlineParameters { selectionId: string; modelId: string }
