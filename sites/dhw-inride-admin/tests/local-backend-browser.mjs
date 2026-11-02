import { createRequire } from 'node:module';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
import path from 'node:path';
import os from 'node:os';
const require=createRequire(path.join(process.env.UI_TEST_TOOLS || '/workspace/release-tools', 'package.json'));
const {chromium}=require('playwright-core');
assert.ok(process.env.INRIDE_LOCAL_BROWSER_FIXTURE, 'Set INRIDE_LOCAL_BROWSER_FIXTURE to the private synthetic fixture file');
const input=JSON.parse(await fs.readFile(process.env.INRIDE_LOCAL_BROWSER_FIXTURE,'utf8'));
assert.equal(input.synthetic_fixture,true,'Only an explicitly synthetic local fixture is allowed');
const site=process.env.INRIDE_ADMIN_BROWSER_URL || 'http://127.0.0.1:3002';
const admin=new URL(site);
assert.equal(admin.hostname,'127.0.0.1','Only an isolated loopback dashboard is allowed');
assert.equal(admin.protocol,'http:');
const api=new URL(input.origin);
assert.equal(api.hostname,'127.0.0.1','Only isolated loopback backend is allowed');
assert.equal(api.protocol,'http:');
assert.equal(api.username+api.password+api.search+api.hash,'');
const report={scope:'Actual Chromium UI against local Auth/PostgREST, no mocked APIs',checks:[],network_statuses:{},page_error_classes:[],external_requests_blocked:0};
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
async function openAccount(label,expected){
 const context=await browser.newContext({viewport:{width:1440,height:900}});
 await context.route('**/*',route=>{
  const u=new URL(route.request().url());
  if(u.hostname==='127.0.0.1'&&[api.port,admin.port].includes(u.port))return route.continue();
  report.external_requests_blocked++;return route.abort();
 });
 const page=await context.newPage();
 page.on('pageerror',e=>report.page_error_classes.push(e.name));
 page.on('response',r=>{const u=new URL(r.url());if(u.origin===api.origin){const key=r.request().method()+' '+u.pathname+' '+r.status();report.network_statuses[key]=(report.network_statuses[key]||0)+1;}});
 await page.goto(site);
 await page.getByRole('heading',{name:'Operations sign in',exact:true}).waitFor();
 const account=input.users[label==='rider'?'rider1':label];
 await page.getByLabel('Email',{exact:true}).fill(account.email);
 await page.getByLabel('Password',{exact:true}).fill(account.password);
 await page.getByRole('button',{name:'Sign in',exact:true}).click();
 await page.getByRole('heading',{name:expected,exact:true}).waitFor({timeout:20000});
 report.checks.push({check:label+' real Auth sign-in and '+expected,status:'PASS'});
 if(label==='admin'){
  await page.reload();
  await page.getByRole('heading',{name:'Overview',exact:true}).waitFor({timeout:20000});
  report.checks.push({check:'admin session restored by page reload',status:'PASS'});
  await page.getByRole('button',{name:'Refresh',exact:true}).click();
  await page.getByRole('heading',{name:'Overview',exact:true}).waitFor();
 }
 await page.getByRole('button',{name:'Sign out',exact:true}).click();
 await page.getByRole('heading',{name:'Operations sign in',exact:true}).waitFor({timeout:15000});
 report.checks.push({check:label+' sign-out returns to sign-in',status:'PASS'});
 await context.close();
}
try{
 for(const [label,expected] of [['admin','Overview'],['passenger','Admin access required'],['rider','Admin access required']])await openAccount(label,expected);
 assert.deepEqual(report.page_error_classes,[],'No uncaught browser exceptions');
 report.status='PASS';
}catch(e){report.status='FAIL';report.failure_class=e.name;report.failure_stage=report.checks.at(-1)?.check||'initial sign-in';}
finally{
 await browser.close();
 await fs.writeFile(process.env.INRIDE_BROWSER_REPORT || path.join(os.tmpdir(),'dhw-local-browser-report.json'),JSON.stringify(report,null,2)+'\n',{mode:0o600});
 console.log(JSON.stringify(report,null,2));
 if(report.status!=='PASS')process.exitCode=1;
}
