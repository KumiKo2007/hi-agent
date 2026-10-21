import { defineConfig } from "@playwright/test"
import { tmpdir } from "node:os"
import { join } from "node:path"

export default defineConfig({
  testDir: ".",
  testMatch: "pr-input-real.spec.ts",
  workers: 1,
  outputDir: join(tmpdir(), "hi-agent-pr-input-browser-results"),
  use: { baseURL: "http://127.0.0.1:3037", channel: "chrome", trace: "retain-on-failure" },
  webServer: {
    command: "npm run dev -- --hostname 127.0.0.1 --port 3037",
    url: "http://127.0.0.1:3037",
    timeout: 120_000,
    env: { NEXT_PUBLIC_API_BASE_URL: process.env.PR_INPUT_API_URL || "http://127.0.0.1:18837/api" },
  },
})
