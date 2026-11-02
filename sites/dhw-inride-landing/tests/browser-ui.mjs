// Real isolated browser checks. Synthetic download fixtures never enter dist/config.
import { createRequire } from 'node:module';
import path from 'node:path';
import fs from 'node:fs/promises';
import os from 'node:os';
import http from 'node:http';
import https from 'node:https';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
const require=createRequire(path.join(process.env.UI_TEST_TOOLS || '/workspace/ui-test-tools','package.json'));
const { chromium }=require('playwright-core');
const AxeBuilder=require('@axe-core/playwright').default;
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../dist');
const artifacts=process.env.UI_TEST_ARTIFACTS || path.join(os.tmpdir(),'inride-landing-ui');
await fs.mkdir(artifacts,{recursive:true});
const temp=await fs.mkdtemp(path.join(os.tmpdir(),'inride-transport-'));
const servers=[];let browser;
const report={scope:'local browser/transport verification; no hosted deployment, release APK or phone install claim',layouts:[],checks:[]};
const mime={'.html':'text/html','.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.jpg':'image/jpeg','.webp':'image/webp'};
function handler(directory,config){return async(req,res)=>{
 try{
  const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
  if(config && pathname==='/release-config.mjs'){res.writeHead(200,{'content-type':'text/javascript'});return res.end(config);}
  const file=path.resolve(directory,'.'+(pathname==='/'?'/index.html':pathname));
  if(!file.startsWith(directory+path.sep)){res.writeHead(403);return res.end();}
  const data=await fs.readFile(file);res.writeHead(200,{'content-type':mime[path.extname(file)]||'application/octet-stream'});res.end(data);
 }catch{res.writeHead(404);res.end('Not found');}
};}
async function listen(server){servers.push(server);await new Promise(r=>server.listen(0,'127.0.0.1',r));return server.address().port;}
try{
 const port=await listen(http.createServer(handler(root)));
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',args:['--no-sandbox'],headless:true});
 for(const width of [320,360,768,1024,1440]){
  const context=await browser.newContext({viewport:{width,height:900},deviceScaleFactor:1});const page=await context.newPage();const errors=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(['error','warning'].includes(m.type()))errors.push(m.text());});
  await page.goto(`http://127.0.0.1:${port}/`,{waitUntil:'networkidle'});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`${width}: overflow`);
  await page.keyboard.press('Tab');assert.equal(await page.locator(':focus').getAttribute('href'),'#main');
  const focus=await page.locator(':focus').evaluate(el=>getComputedStyle(el).outlineStyle);assert.notEqual(focus,'none');
  await page.keyboard.press('Enter');assert.equal(await page.locator(':focus').getAttribute('id'),'main');
  assert.equal(await page.locator('header .admin-entry').isVisible(),true);
  assert.equal(await page.locator('button.download-action:disabled').count(),2);
  assert.equal(await page.locator('a.download-action').count(),0);
  if(width<=1020){await page.locator('.menu-toggle').click();assert.equal(await page.locator('#main-menu').isVisible(),true);await page.keyboard.press('Escape');assert.equal(await page.locator('#main-menu').isVisible(),false);}
  await page.locator('header .admin-entry').click();await page.locator('#admin-status').waitFor();assert.match(await page.locator('#admin-status').innerText(),/not been hosted/);
  await page.locator('#installation-guide summary').click();assert.equal(await page.locator('#installation-guide li').count(),5);assert.equal(await page.locator('#installation-guide').getAttribute('open'),'');
  const a11y=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();
  assert.deepEqual(a11y.violations.map(v=>({id:v.id,impact:v.impact,nodes:v.nodes.length})),[],`${width}: accessibility violations`);
  assert.deepEqual(errors,[],`${width}: console errors/warnings`);
  await page.goto(`http://127.0.0.1:${port}/`);await page.reload();assert.equal(await page.locator('h1').count(),1);
  if([360,1440].includes(width)){await page.screenshot({path:path.join(artifacts,`landing-${width}.jpg`),type:'jpeg',quality:82,fullPage:true});await page.screenshot({path:path.join(artifacts,`landing-${width}-hero.jpg`),type:'jpeg',quality:85});}
  report.layouts.push({width,overflow:false,axe_violations:0,console_errors:0,keyboard_and_reload:'PASS',downloads:'Both pending',admin:'Visible; actual hosting absent'});await context.close();
 }
 for(const width of [320,1440]){const c=await browser.newContext({javaScriptEnabled:false,viewport:{width,height:900}});const p=await c.newPage();await p.goto(`http://127.0.0.1:${port}/`);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert.equal(await p.locator('button.download-action:disabled').count(),2);assert.equal(await p.locator('header .admin-entry').isVisible(),true);assert.equal(await p.locator('#main-menu').isVisible(),true);report.checks.push(`No-JS ${width}: honest pending states/navigation/no overflow`);await c.close();}
 await browser.close();browser=null;
 // Trust only this ephemeral test certificate's public-key pin, never all HTTPS errors.
 const key=path.join(temp,'key.pem'),cert=path.join(temp,'cert.pem');
 execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-keyout',key,'-out',cert,'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'],{stdio:'ignore'});
 const publicKey=execFileSync('openssl',['x509','-pubkey','-noout','-in',cert]);
 const spki=execFileSync('openssl',['pkey','-pubin','-outform','DER'],{input:publicKey});
 const pin=createHash('sha256').update(spki).digest('base64');const tls={key:await fs.readFile(key),cert:await fs.readFile(cert)};
 const payload=Buffer.from('Synthetic browser transport fixture; this is not an APK.');
 const hash=createHash('sha256').update(payload).digest('hex');
 const filePort=await listen(https.createServer(tls,(req,res)=>{res.writeHead(200,{'content-type':'application/vnd.android.package-archive','content-disposition':'attachment; filename="fixture-transport.apk"'});res.end(payload);}));
 const release=app=>({kind:'apk',url:`https://127.0.0.1:${filePort}/${app}.apk`,version:'0.0.0-transport-fixture',bytes:payload.length,android:'8.0',releaseDate:'2026-11-02',packageName:`zm.co.dhwinride.${app}`,sha256:hash,certificateSha256:'b'.repeat(64)});
 let adminUrl=null;
 if(process.env.ADMIN_EXPORT_DIR){const directory=path.resolve(process.env.ADMIN_EXPORT_DIR);await fs.access(path.join(directory,'index.html'));const adminPort=await listen(https.createServer(tls,handler(directory)));adminUrl=`https://127.0.0.1:${adminPort}/`;}
 const config=`export const releases=${JSON.stringify({passenger:release('passenger'),rider:release('rider')})};export const adminUrl=${JSON.stringify(adminUrl)};`;
 const uiPort=await listen(https.createServer(tls,handler(root,config)));
 browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox',`--ignore-certificate-errors-spki-list=${pin}`]});
 const page=await browser.newPage({acceptDownloads:true});await page.goto(`https://127.0.0.1:${uiPort}/`);
 assert.match(await page.locator('#app-availability').innerText(),/Verified APKs are available/);
 for(const app of ['passenger','rider']){
  const card=page.locator(`[data-app="${app}"]`);assert.equal(await card.locator('[data-field="sha256"]').textContent(),hash);
  const waiting=page.waitForEvent('download');await card.locator('a.download-action').click();const download=await waiting;
  assert.equal(download.suggestedFilename(),'fixture-transport.apk');const output=path.join(temp,app+'.bin');await download.saveAs(output);const bytes=await fs.readFile(output);assert.equal(bytes.length,payload.length);assert.equal(createHash('sha256').update(bytes).digest('hex'),hash);
 }
 report.checks.push('Two actual cross-origin HTTPS attachment downloads: size/checksum match synthetic fixture; not APK validation');
 if(adminUrl){await page.locator('header .admin-entry').click();await page.getByRole('heading',{name:'Operations sign in',exact:true}).waitFor();assert.equal(new URL(page.url()).pathname,'/');await page.reload();await page.getByRole('heading',{name:'Operations sign in',exact:true}).waitFor();await page.goto(`https://127.0.0.1:${uiPort}/`);await page.locator('footer .admin-entry').click();await page.getByRole('heading',{name:'Operations sign in',exact:true}).waitFor();report.checks.push('Both Admin links open actual local admin export root sign-in; direct refresh works; hosted origin remains unavailable');}
 await fs.writeFile(path.join(artifacts,'browser-report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}finally{if(browser)await browser.close();await Promise.all(servers.map(s=>new Promise(r=>s.close(r))));await fs.rm(temp,{recursive:true,force:true});}
