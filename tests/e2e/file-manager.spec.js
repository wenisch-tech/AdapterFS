const { test, expect } = require('@playwright/test');
test('local login and file manager journey', async ({page}) => {
  await page.goto('/login');
  await page.getByLabel('Username').fill(process.env.ADAPTERFS_USERNAME || 'admin');
  await page.getByLabel('Password').fill(process.env.ADAPTERFS_PASSWORD || 'adapterfs-demo');
  await page.getByRole('button',{name:'Sign in'}).click();
  await expect(page.getByRole('heading',{name:'files'})).toBeVisible();
  await expect(page.getByText('README.txt')).toBeVisible();
  await page.getByPlaceholder('Filter files').fill('README');
  await expect(page.getByText('README.txt')).toBeVisible();
  await page.getByPlaceholder('Filter files').fill('');
  await page.getByRole('button',{name:'Connections'}).click();
  await expect(page.getByRole('heading',{name:/Connect to files/})).toBeVisible();
});

test('creates, uploads, renames and deletes from the browser', async ({page}) => {
  await page.goto('/login');
  await page.getByLabel('Username').fill(process.env.ADAPTERFS_USERNAME || 'admin');
  await page.getByLabel('Password').fill(process.env.ADAPTERFS_PASSWORD || 'adapterfs-demo');
  await page.getByLabel('Password').press('Enter');
  const suffix=Date.now(), folder=`ui-${suffix}`, original=`upload-${suffix}.txt`, renamed=`renamed-${suffix}.txt`;
  await page.getByRole('button',{name:'New folder'}).click();await page.locator('#folder-name').fill(folder);await page.getByRole('button',{name:'Create'}).click();
  await expect(page.getByText(folder,{exact:true})).toBeVisible();
  await page.locator('#upload').setInputFiles({name:original,mimeType:'text/plain',buffer:Buffer.from('browser upload')});
  await expect(page.getByText(original,{exact:true})).toBeVisible();
  await page.getByText(original,{exact:true}).locator('xpath=ancestor::tr').locator('.row-menu').click();
  page.once('dialog',dialog=>dialog.accept(renamed));await page.getByRole('button',{name:'Rename'}).click();
  await expect(page.getByText(renamed,{exact:true})).toBeVisible();
  await page.getByText(renamed,{exact:true}).locator('xpath=ancestor::tr').locator('.row-menu').click();
  page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'Delete'}).click();await expect(page.getByText(renamed,{exact:true})).toHaveCount(0);
  await page.getByText(folder,{exact:true}).locator('xpath=ancestor::tr').locator('.row-menu').click();
  page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'Delete'}).click();await expect(page.getByText(folder,{exact:true})).toHaveCount(0);
});

test('works at a mobile viewport with keyboard login', async ({page}) => {
  await page.setViewportSize({width:375,height:760});await page.goto('/login');
  await page.keyboard.press('Tab');await page.keyboard.type(process.env.ADAPTERFS_USERNAME || 'admin');await page.keyboard.press('Tab');await page.keyboard.type(process.env.ADAPTERFS_PASSWORD || 'adapterfs-demo');await page.keyboard.press('Enter');
  await expect(page.locator('#mobile-menu')).toBeVisible();await page.locator('#mobile-menu').click();await expect(page.locator('.sidebar')).toHaveClass(/open/);
});
