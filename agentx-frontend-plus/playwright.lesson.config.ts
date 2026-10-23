import { defineConfig } from "@playwright/test"
export default defineConfig({
  testDir: "./integration", outputDir: "../.playwright-lesson-results", testMatch: "teaching-lesson.spec.ts", retries: 0, workers: 1, reporter: "list",
  use: { baseURL: "http://127.0.0.1:3029", channel: "chrome", trace: "retain-on-failure" },
  webServer: { command: "npm run dev -- --hostname 127.0.0.1 --port 3029", url: "http://127.0.0.1:3029", reuseExistingServer: false, timeout: 120_000 },
})
