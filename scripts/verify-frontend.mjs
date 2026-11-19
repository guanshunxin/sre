// 连接本项目页面，用真实订单验证业务 UI；不连接 AI-SRE，不调用模型或群消息。
import assert from 'node:assert/strict';import fs from 'node:fs/promises';import {pathToFileURL,fileURLToPath} from 'node:url';
const moduleUrl=process.env.PLAYWRIGHT_MODULE?pathToFileURL(process.env.PLAYWRIGHT_MODULE).href:new URL('../.runtime/qa/node_modules/playwright/index.mjs',import.meta.url).href;
const {chromium}=await import(moduleUrl);
const env=Object.fromEntries((await fs.readFile(new URL('../.env',import.meta.url),'utf8')).split(/\r?\n/).filter(Boolean).map(x=>{const i=x.indexOf('=');return[x.slice(0,i),x.slice(i+1)];}));
const base='http://127.0.0.1:'+(env.WEB_PORT||18888),output=new URL('../docs/screenshots/',import.meta.url);await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true,...(process.platform==='win32'?{channel:'msedge'}:{})});
const errors=[],leaks=[],pending=[];
try{
 const page=await browser.newPage({viewport:{width:1600,height:1050}});page.on('pageerror',e=>errors.push(e.message));
 page.on('response',r=>{if(r.url().startsWith(base+'/api/'))pending.push(r.text().then(text=>{if([env.SHOP_API_TOKEN,env.SHOP_METRICS_TOKEN,env.SHOP_LAB_TOKEN].some(s=>text.includes(s)))leaks.push('response credential');}).catch(()=>{}));});
 await page.goto(base);await page.getByLabel('业务访问口令',{exact:true}).fill(env.SHOP_API_TOKEN);await page.getByRole('button',{name:'连接并刷新',exact:true}).click();
 await page.getByRole('heading',{name:'机械键盘',exact:true}).waitFor();assert.equal(await page.locator('.products article').count(),3);
 async function shot(name){assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No horizontal overflow');await page.screenshot({path:fileURLToPath(new URL(name,output)),fullPage:true,mask:[page.locator('input[type=password]')],maskColor:'#dfe6ea'});}
 await shot('catalog-desktop.png');
 await page.getByRole('button',{name:'下单并预留库存',exact:true}).first().click();await page.getByRole('status').filter({hasText:'已受理'}).waitFor();
 await page.getByRole('button',{name:'订单记录'}).click();await page.locator('tbody tr').first().waitFor();assert.ok((await page.locator('tbody').innerText()).includes('SKU'));
 await shot('orders-desktop.png');
 await page.getByRole('button',{name:'故障演练'}).click();await page.getByLabel('独立演练口令',{exact:true}).fill(env.SHOP_LAB_TOKEN);await page.getByRole('button',{name:'读取实际状态',exact:true}).click();await page.locator('.lab-panel pre').waitFor();
 assert.ok((await page.locator('.lab-panel pre').innerText()).includes('NONE'));await shot('lab-desktop.png');
 await page.setViewportSize({width:390,height:844});await shot('lab-mobile.png');await page.getByRole('button',{name:'商品与库存'}).click();await shot('catalog-mobile.png');
 const storage=await page.evaluate(()=>JSON.stringify(localStorage)+JSON.stringify(sessionStorage));assert.ok(![env.SHOP_API_TOKEN,env.SHOP_LAB_TOKEN].some(s=>storage.includes(s)));
 await Promise.all(pending);assert.deepEqual(errors,[]);assert.deepEqual(leaks,[]);
 await fs.writeFile(new URL('../.runtime/frontend-verification.json',import.meta.url),JSON.stringify({status:'PASSED',at:new Date().toISOString(),actualBusinessOrder:true,products:3,screenshots:5,desktopAndMobile:true,browserErrors:0,responseCredentialLeaks:0,browserStorageCredentialLeaks:0},null,2)+'\n');
 console.log('PASS actual catalog/order/lab UI, desktop/mobile and memory-only credentials.');
}finally{await browser.close();}
