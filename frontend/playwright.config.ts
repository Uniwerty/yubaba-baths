import {defineConfig} from "@playwright/test";

export default defineConfig({
    testDir: "./tests",
    timeout: 90000,
    expect: {timeout: 15000},
    workers: 1,
    use: {
        actionTimeout: 10000,
        baseURL: process.env.BASE_URL || "http://localhost:8080",
        viewport: {width: 1440, height: 1000},
        trace: "retain-on-failure",
        screenshot: "only-on-failure",
    },
    reporter: [["list"], ["html", {open: "never"}]],
});
