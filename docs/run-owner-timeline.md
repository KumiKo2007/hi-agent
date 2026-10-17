# Owner run timeline data contract

## Threat model

Run goals, Agent prompts, retrieved documents, model responses, MCP tool arguments, and MCP tool results can contain arbitrary user or third-party text. Run IDs and event IDs are identifiers, not authorization. The principal protected assets are GitHub connection credentials, user-provided data, immutable approved writes, and the owner-only activity history.

- The run detail service must authorize by `(runId, currentUserId)` before reading timeline events. A missing or foreign run remains indistinguishable to the API.
- The GitHub credential remains in the authorized MCP transport adapter. It must never be added to model messages, timeline rows, approval previews, or logs. Any JSON field named like a credential, header, or cookie is redacted as a whole, including nested header values such as `Cookie` and `User-Agent`.
- The database stores the exact immutable approval arguments used for execution. The owner-facing projection may redact credential-like fields and values, but must preserve the reviewable issue title, body, repository, and citations.
- Timeline events are append-only and created only from typed internal event factories. No generic map, prompt, raw tool argument, header, arbitrary model/tool output, or unfiltered exception message is an event field.
- Retrieved source bodies are model context only. The timeline records source IDs, file names, page numbers, and the authenticated source URL, after bounded string redaction; it never stores source content.
- Failures use a finite code and generic/actionable summary. If an error cannot be mapped safely, display a generic failure reason. Ordinary run logs contain run identifiers and state transitions only.

## Event fields

Every event has an event ID, run ID, step ID when known, database-generated append order, type, status, safe summary, and timestamp. Event-specific allowlisted fields are:

| Event | Allowed additional fields |
| --- | --- |
| `RUN_CREATED`, `RUN_ATTEMPT_STARTED`, `RUN_COMPLETED`, `RUN_FAILED`, `RUN_CANCELLED` | finite failure code when applicable |
| `KNOWLEDGE_RETRIEVAL` | `COMPLETED` / `INSUFFICIENT` status and pinned knowledge version ID |
| `KNOWLEDGE_SOURCE` | document ID, file ID/name, page, authenticated source URL |
| `MODEL_CALL` | model name, input/output token counts (nullable only when provider usage is unavailable), usage status, elapsed model-call milliseconds |
| `TOOL_ACTION` | service/action name, selected repository, lifecycle status, validated external resource reference when available |
| `APPROVAL_DECISION` | approver ID, `APPROVED` / `DENIED`, and decision time |
| `RECONCILIATION` | finite reconciliation outcome and safe status |

The API returns these fields through an explicit DTO projection. Unknown event types/fields are not serialized. The response aggregates reported input/output tokens and marks totals incomplete when any model call lacks usage. It reports no currency cost because providers do not expose a common run pricing contract here. Model-call duration measures only that call; no `createdAt` to final `updatedAt` duration is presented as active execution time because approval and reconciliation waits may span hours.

## Next-action contract

`nextAction` is a finite value derived from the authorized run state and latest safe failure code:

- `OWNER_DECISION` for a pending approval.
- `REVIEW_TOOL_SCHEMA` for schema review.
- `RECONCILE_EXTERNAL_WRITE` for an uncertain external write.
- `ADD_OR_SELECT_EVIDENCE` for insufficient evidence.
- `CHECK_REPOSITORY_AUTHORIZATION` for a denied target or permission.
- `CHECK_MODEL_CONNECTION` for an unavailable model/provider.
- `START_NEW_RUN` for other retryable terminal failures.
- `WAIT` while pending/running and `NONE` for completed/cancelled runs.

These values guide the UI; they do not authorize writes or change run transitions.
