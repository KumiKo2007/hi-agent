import { defineConfig } from "@playwright/test"

export default defineConfig({
  testDir: "./integration",
  testMatch: process.env.TEACHING_SLICE_ONLY ? "teaching-read.spec.ts" : "teaching-real.spec.ts",
  retries: 0,
  workers: 1,
  reporter: "list",
  use: {
    baseURL: process.env.TEACHING_WEB_URL || "http://127.0.0.1:3025",
    channel: "chrome",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "npm run dev -- --hostname 127.0.0.1 --port 3025",
    url: "http://127.0.0.1:3025",
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
