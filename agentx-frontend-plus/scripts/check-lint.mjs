import { spawnSync } from "node:child_process"
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { join, relative, sep } from "node:path"
import { createRequire } from "node:module"
import { fileURLToPath } from "node:url"

const require = createRequire(import.meta.url)
const projectRoot = fileURLToPath(new URL("..", import.meta.url))
const baselinePath = fileURLToPath(new URL("../lint-baseline.json", import.meta.url))
const update = process.argv.includes("--update")
const tempDir = mkdtempSync(join(tmpdir(), "hi-agent-lint-"))
const reportPath = join(tempDir, "report.json")

try {
  const result = spawnSync(process.execPath, [
    require.resolve("next/dist/bin/next"), "lint", "--dir", ".", "--format", "json", "--output-file", reportPath,
  ], { cwd: projectRoot, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 })

  if (result.error || ![0, 1].includes(result.status)) {
    throw new Error(result.error?.message || result.stderr || result.stdout || "ESLint 无法运行")
  }

  const report = JSON.parse(readFileSync(reportPath, "utf8"))
  const violations = new Map()
  let errors = 0
  let warnings = 0

  for (const file of report) {
    if (file.fatalErrorCount > 0) {
      throw new Error(`ESLint 无法分析 ${file.filePath}`)
    }
    const path = relative(projectRoot, file.filePath).split(sep).join("/")
    const sourceLines = file.source?.split(/\r?\n/) || []
    errors += file.errorCount
    warnings += file.warningCount

    for (const message of file.messages) {
      const entry = {
        file: path,
        rule: message.ruleId,
        severity: message.severity,
        message: message.message,
        source: (sourceLines[message.line - 1] || "").trim(),
      }
      const key = JSON.stringify(entry)
      violations.set(key, (violations.get(key) || 0) + 1)
    }
  }

  const sorted = [...violations].sort(([left], [right]) => left.localeCompare(right, "en"))
  if (update) {
    writeFileSync(baselinePath, `${JSON.stringify({ version: 1, violations: sorted }, null, 2)}\n`)
    console.log(`已更新 lint 基线：${errors} 个错误，${warnings} 个警告。`)
    process.exit(0)
  }

  const baseline = JSON.parse(readFileSync(baselinePath, "utf8"))
  if (baseline.version !== 1) throw new Error("不支持的 lint 基线版本")
  const allowed = new Map(baseline.violations)
  const added = sorted.filter(([key, count]) => count > (allowed.get(key) || 0))
  if (added.length > 0) {
    for (const [key, count] of added) {
      const entry = JSON.parse(key)
      console.error(`${entry.file}: 新增 ${entry.rule} 诊断（${count - (allowed.get(key) || 0)} 个）：${entry.source}`)
    }
    process.exitCode = 1
  } else {
    console.log(`lint 基线通过。现有问题：${errors} 个错误，${warnings} 个警告；没有新增诊断。`)
  }
} catch (error) {
  console.error(error)
  process.exitCode = 1
} finally {
  rmSync(tempDir, { recursive: true, force: true })
}
