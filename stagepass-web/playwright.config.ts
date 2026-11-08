import { defineConfig, devices } from '@playwright/test'

const testEnv = {
  VITE_API_BASE_URL: 'http://api.test',
  VITE_SUPABASE_URL: 'https://test.supabase.co',
  VITE_SUPABASE_ANON_KEY: 'e2e-anon-key',
}

/**
 * Smoke tests. The API (http://api.test) and Supabase are faked inside the browser (see e2e/fixtures), so these run
 * in CI without secrets or live services. The "csp" project runs the production build (vite preview) under the
 * enforced Content-Security-Policy from render.yaml.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'github' : 'list',
  use: { trace: 'retain-on-failure' },
  projects: [
    {
      name: 'desktop',
      testIgnore: /csp\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], baseURL: 'http://localhost:5180', viewport: { width: 1280, height: 800 } },
    },
    {
      name: 'phone',
      testIgnore: /csp\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], baseURL: 'http://localhost:5180', viewport: { width: 375, height: 812 }, isMobile: true, hasTouch: true },
    },
    {
      name: 'csp',
      testMatch: /csp\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], baseURL: 'http://localhost:5181' },
    },
  ],
  webServer: [
    {
      command: 'npx vite --port 5180 --strictPort',
      url: 'http://localhost:5180',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: testEnv,
    },
    {
      command: 'npx vite build --outDir dist-e2e --emptyOutDir && npx vite preview --outDir dist-e2e --port 5181 --strictPort',
      url: 'http://localhost:5181',
      reuseExistingServer: !process.env.CI,
      timeout: 240_000,
      env: testEnv,
    },
  ],
})
