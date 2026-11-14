import { expect, test } from '@playwright/test';

/**
 * The one browser-level journey that component tests cannot cover:
 * real browser → production SPA bundle → session cookie → nginx proxy →
 * Spring Security → PostgreSQL, against the docker compose `full` stack.
 *
 * Playwright's webServer boots the stack and waits for the backend to
 * finish seeding its demo dataset before this test starts.
 */
test('owner logs in and sees the seeded catalog', async ({ page }) => {
  await page.goto('/login');

  await page.getByRole('button', { name: 'Use owner demo' }).click();
  await page.getByRole('button', { name: 'Sign in' }).click();

  // Login without a `next` param lands on the default landing route.
  await expect(page).toHaveURL(/\/stock\/products$/);

  // The header proves RequireAuth passed; the table proves real seeded
  // data came back through the API from the database. Scoped to the table
  // because the mobile card list also renders product names (hidden but
  // present in the DOM on desktop viewports).
  await expect(page.getByRole('columnheader', { name: 'Product / variant' })).toBeVisible();
  await expect(
    page.getByRole('table').getByText('Heavyweight Cotton Hoodie', { exact: true }),
  ).toBeVisible();
});
