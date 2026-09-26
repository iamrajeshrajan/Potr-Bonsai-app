import path from 'node:path';
import { cloudflareTest, readD1Migrations } from '@cloudflare/vitest-pool-workers';
import { defineConfig } from 'vitest/config';

export default defineConfig(async () => {
  const migrations = await readD1Migrations(path.join(import.meta.dirname, 'migrations'));
  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: './wrangler.toml' },
        miniflare: {
          bindings: {
            TEST_MIGRATIONS: migrations,
            PUBLIC_BASE_URL: 'https://bonsai.test',
            BOOTSTRAP_SECRET: 'bootstrap-secret',
            WOO_URL: 'https://shop.test',
            WOO_CONSUMER_KEY: 'ck_test',
            WOO_CONSUMER_SECRET: 'cs_test',
            WOO_CATEGORY_BONSAI_ID: '11',
            WOO_CATEGORY_POTS_ID: '12',
            WOO_WEBHOOK_SECRET: 'hook-secret',
          },
        },
      }),
    ],
    test: { setupFiles: ['./test/setup.ts'] },
  };
});
