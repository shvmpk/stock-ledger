import { defineConfig } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';

// Boots the full containerized Outer Loop stack (Postgres + distroless
// backend + nginx frontend) from the repository root. `--wait` returns
// once every container is up, but the dev-profile backend keeps seeding
// its demo dataset after boot, so poll a real login against the seeded
// owner account until the backend is genuinely ready.
const startStackCommand = [
  'docker compose -f ../docker-compose.yml --profile full up -d --build --wait',
  '&& until curl -sf -o /dev/null -X POST http://localhost:3000/api/v1/auth/login',
  `-H 'Content-Type: application/json'`,
  `-d '{"email":"owner@stockledger.io","password":"OwnerPass2026!"}'; do`,
  'i=$((i+1)); if [ "$i" -ge 90 ]; then',
  "echo 'e2e backend never became ready; dumping backend logs:'",
  '&& docker compose -f ../docker-compose.yml --profile full logs --tail 200 backend && exit 1; fi; sleep 2; done',
].join(' ');

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
  webServer: {
    command: startStackCommand,
    url: `${baseURL}/actuator/health`,
    reuseExistingServer: !process.env.CI,
    timeout: 15 * 60_000,
  },
});
