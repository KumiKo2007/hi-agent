import type { OutlineParameters, TeachingOutline } from "@/types/teaching-outline"

export interface LessonEvidence {
  kind: "SOURCE" | "SUPPLEMENT" | "UNSUPPORTED"
  sourceIds: string[]
  note: string
}
export interface LessonBlock { id: string; title: string; text: string; evidence: LessonEvidence }
export interface LessonExample extends LessonBlock { solution: string }
export interface LessonSource {
  id: string; fileId: string; fileName: string; locatorType: string; physicalPage: number | null
  paragraphIndex?: number | null; heading?: string | null; content: string; coverageWarnings?: string[]
}
export interface LessonContent {
  schemaVersion: number; title: string
  student: { objectives: string[] }
  teacher: { objectives: string[]; difficulties: string[]; stages: { title: string; minutes: number; guidance: string }[]; prompts: string[]; misconceptions: string[] }
  knowledge: LessonBlock[]; derivations: LessonBlock[]; examples: LessonExample[]
  sources: LessonSource[]; reviewStatus: "TEACHER_REVIEW_REQUIRED"
}
export interface LessonRevision {
  id: string; artifactId: string; runId: string; inputRevisionId: string; knowledgeVersionId: string
  parentRevisionId: string | null; content: LessonContent; createdAt: string
}
export interface TeachingLesson {
  id: string; kind: "TEACHING_LESSON"; selectionId: string; knowledgeVersionId: string; inputRevisionId: string
  currentRevisionId: string | null; parameters: OutlineParameters & { outlineId: string; lessonNumber: number; lessonTitle: string; lessonObjectives: string[] }
  run: TeachingOutline["run"]; revision: LessonRevision | null
}
export interface CreateTeachingLesson { outlineId: string; expectedOutlineRevisionId: string; lessonNumber: number; modelId: string }
