import { defineConfig } from "@playwright/test"
export default defineConfig({
  testDir: "./integration", outputDir: "../.playwright-outline-results", testMatch: "teaching-outline.spec.ts", retries: 0, workers: 1, reporter: "list",
  use: { baseURL: "http://127.0.0.1:3028", channel: "chrome", trace: "retain-on-failure" },
  webServer: { command: "npm run dev -- --hostname 127.0.0.1 --port 3028", url: "http://127.0.0.1:3028", reuseExistingServer: true, timeout: 120_000 },
})
