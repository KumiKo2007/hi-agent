# Versioned Agent run evaluations

The deterministic v1 corpus is checked in at `AgentX/src/test/resources/evaluations/v1.json`. It covers cited evidence, issue title and body, the pinned repository, selected tool action, insufficient evidence, missing repository authorization, and an approved tool failure that requires reconciliation.

Run the corpus from `AgentX`:

```sh
./mvnw -Dtest=VersionedRunEvaluationTest test
```

The runner writes `AgentX/target/evaluations/run-evaluation-v1.json`. The report contains a stable corpus version and case IDs, pass/fail status, run and step IDs, active duration, input/output tokens, and cost. The summary includes pass rate, elapsed active time, token totals, and the versioned rate-card identity. CI uploads this JSON as a per-workflow artifact for later comparison.

The fixture model reports fixed token counts and uses the corpus rate card (`fixture-usd-v1`: USD 0.10 per million input tokens and USD 0.40 per million output tokens). These amounts are synthetic comparison metrics, not provider billing. Assertions check behavior and structured fields; they do not compare randomized model prose.

Active duration sums only the time spent inside each durable worker attempt. Time between attempts, including owner approval and reconciliation waits, is not measured. A report row carries the actual `runId` and `stepId`; an evaluator failure also sets `failureRunId` and `failureStepId` so the failing run step can be opened directly.

To calculate deltas against a saved report, preserve the previous JSON outside the output path and pass it on the next run:

```sh
cp target/evaluations/run-evaluation-v1.json /tmp/run-evaluation-previous.json
./mvnw -Dtest=VersionedRunEvaluationTest \
  -Drun.evaluation.previousReport=/tmp/run-evaluation-previous.json test
```

The comparison is rejected when the corpus or rate-card versions differ. Bump the corpus or rate-card version when changing fixtures, scoring rules, or synthetic prices so reports remain comparable within each version.
