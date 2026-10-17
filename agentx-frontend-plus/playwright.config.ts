import { defineConfig } from "@playwright/test"

const noProxyHosts = new Set([
  ...(process.env.NO_PROXY || "").split(","),
  ...(process.env.no_proxy || "").split(","),
  "127.0.0.1",
  "localhost",
  "::1",
].map(host => host.trim()).filter(Boolean))
const noProxy = [...noProxyHosts].join(",")
process.env.NO_PROXY = noProxy
process.env.no_proxy = noProxy

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: "list",
  use: {
    baseURL: "http://127.0.0.1:3000",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "npm run dev -- --hostname 127.0.0.1 --port 3000",
    url: "http://127.0.0.1:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
