const { defineConfig } = require('@playwright/test');
module.exports = defineConfig({
  testDir: './tests/e2e', timeout: 30000, retries: 0,
  use: { baseURL: process.env.ADAPTERFS_BASE_URL || 'http://127.0.0.1:18080', viewport: {width: 1280,height: 800}, screenshot: 'only-on-failure' }
});
