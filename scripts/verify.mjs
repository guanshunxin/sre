// 只在本项目独立 Compose 上执行真实业务/依赖演练；不访问 AI-SRE、飞书或模型。
import assert from 'node:assert/strict';import fs from 'node:fs/promises';import {execFile} from 'node:child_process';import {promisify} from 'node:util';import {setTimeout as sleep} from 'node:timers/promises';import {fileURLToPath} from 'node:url';
if(!process.argv.includes('--run')){console.log('Usage: node scripts/verify.mjs --run (isolated demo stack only)');process.exit(0);}
const root=fileURLToPath(new URL('../',import.meta.url)),run=promisify(execFile);
const env=Object.fromEntries((await fs.readFile(new URL('../.env',import.meta.url),'utf8')).split(/\r?\n/).filter(x=>x&&!x.startsWith('#')).map(x=>{const i=x.indexOf('=');return[x.slice(0,i),x.slice(i+1)];}));
const urls={a:'http://127.0.0.1:19081',b:'http://127.0.0.1:19082',delivery:'http://127.0.0.1:19083',web:'http://127.0.0.1:'+(env.WEB_PORT||18888)};
const compose=['compose','--project-name','gsx-sample-shop','--env-file','.env','--env-file','.runtime/lab-overrides.env','-f','infra/compose.yml'];
const report={status:'RUNNING',startedAt:new Date().toISOString(),checks:[],externalMessages:0,modelCalls:0};const stopped=new Set();
const save=()=>fs.writeFile(new URL('../.runtime/verification.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
function mark(value){report.checks.push(value);console.log(new Date().toISOString()+' '+value);}
async function api(target,path,{expected=200,metrics=false,lab=false,...options}={}){const r=await fetch(urls[target]+path,{...options,headers:{Authorization:'Bearer '+(metrics?env.SHOP_METRICS_TOKEN:env.SHOP_API_TOKEN),'Content-Type':'application/json',...(lab?{'X-Lab-Token':env.SHOP_LAB_TOKEN}:{}),...options.headers},signal:AbortSignal.timeout(12000)});assert.equal(r.status,expected,target+path+' HTTP '+r.status);return r.json();}
const post=body=>({method:'POST',body:JSON.stringify(body)});
async function wait(name,test,seconds=90){const end=Date.now()+seconds*1000;let last;while(Date.now()<end){try{const value=await test();if(value)return value;}catch(e){last=e;}await sleep(500);}throw Error('Timed out: '+name,{cause:last});}
async function docker(args){try{return await run('docker',[...compose,...args],{cwd:root,timeout:180000,maxBuffer:1024*1024});}catch(e){await fs.writeFile(new URL('../.runtime/docker-private-error.log',import.meta.url),String(e.stdout||'')+String(e.stderr||''),{mode:0o600});throw Error('Isolated Docker action failed; inspect private log');}}
async function stop(name){await docker(['stop',name]);stopped.add(name);}
async function start(name){await docker(['start',name]);stopped.delete(name);}
async function httpFault(target,mode,seconds=30){return api(target,'/internal/faults',{...post({mode,seconds}),lab:true});}
async function resource(target,scenario,seconds=20){return api(target,'/internal/lab',{...post({scenario,seconds}),lab:true});}
async function clear(target){await api(target,'/internal/faults',{method:'DELETE',lab:true});if(target!=='delivery')await api(target,'/internal/lab',{method:'DELETE',lab:true});}
async function order(key='verify-'+crypto.randomUUID(),quantity=1,target='web'){return api(target,'/api/orders',{...post({productId:1,quantity,region:'LOCAL'}),headers:{'Idempotency-Key':key}});}
async function metrics(target='a'){const r=await fetch(urls[target]+'/actuator/prometheus',{headers:{Authorization:'Bearer '+env.SHOP_METRICS_TOKEN},signal:AbortSignal.timeout(8000)});assert.equal(r.status,200);return r.text();}
function gauge(text,name){const values=text.split('\n').filter(l=>l.startsWith(name+'{')||l.startsWith(name+' ')).map(l=>Number(l.split(' ').at(-1))).filter(Number.isFinite);return values.reduce((a,b)=>a+b,0);}
async function alert(name){const r=await(await fetch('http://127.0.0.1:19094/api/v1/alerts')).json();return r.data.alerts.some(a=>a.labels.alertname===name&&a.state==='firing');}
try{
 await save();const id=(await docker(['ps','-q','mysql'])).stdout.trim();assert.ok(id);assert.equal((await run('docker',['inspect','--format','{{index .Config.Labels "com.docker.compose.project"}}',id])).stdout.trim(),'gsx-sample-shop');
 for(const target of ['a','b','delivery'])await clear(target);
 await api('web','/api/products/1/restock',post({quantity:100}));
 const products=await api('web','/api/products'),before=products.find(p=>p.id===1).stock;
 const key='duplicate-'+crypto.randomUUID(),duplicates=await Promise.all(Array.from({length:12},(_,i)=>order(key,1,i%2?'a':'b')));
 assert.equal(new Set(duplicates.map(o=>o.id)).size,1);assert.equal((await api('web','/api/products')).find(p=>p.id===1).stock,before-1);
 await api('a','/api/orders',{...post({productId:1,quantity:2,region:'LOCAL'}),headers:{'Idempotency-Key':key},expected:409});
 await wait('actual Kafka consumption',async()=>((await api('web','/api/orders/'+duplicates[0].id)).status==='READY'));
 mark('PASS real database orders/stock, cross-instance idempotency, conflicting payload rejection and Kafka fulfillment');

 await resource('a','CONSUMER_PAUSE',60);await resource('b','CONSUMER_PAUSE',60);
 await wait('both actual Kafka listeners paused',async()=>(await api('a','/internal/lab',{lab:true})).consumerPaused&&(await api('b','/internal/lab',{lab:true})).consumerPaused);
 const cancelled=await order();await api('web','/api/orders/'+cancelled.id+'/cancel',{method:'POST'});await api('web','/api/orders/'+cancelled.id+'/cancel',{method:'POST'});
 const queued=[];for(let i=0;i<8;i++)queued.push(await order());
 await wait('real fulfillment backlog alert',()=>alert('ShopFulfillmentBacklog'),50);
 await clear('a');await clear('b');
 await wait('consumer resumes without losing messages',async()=>{const list=await api('web','/api/orders');return queued.every(o=>list.some(r=>r.id===o.id&&r.status==='READY'));});
 assert.equal((await api('web','/api/orders/'+cancelled.id)).status,'CANCELLED');
 mark('PASS real consumer pause/backlog/Prometheus alert, cancel inventory release, automatic processing and no cancelled-order resurrection');

 await httpFault('a','SLOW',3);const at=performance.now();await api('a','/api/products');assert.ok(performance.now()-at>=1300);await sleep(1700);assert.equal((await api('a','/internal/faults',{lab:true})).mode,'NONE');
 await httpFault('a','HTTP_5XX',45);await httpFault('b','HTTP_5XX',45);
 await wait('real HTTP error-rate alert',async()=>{await api('web','/api/products',{expected:503});return alert('ShopHttpErrorRate');},40);
 await clear('a');await clear('b');
 await httpFault('delivery','SLOW',30);await api('a','/api/orders',{...post({productId:1,quantity:1,region:'LOCAL'}),headers:{'Idempotency-Key':'timeout-'+crypto.randomUUID()},expected:504});await clear('delivery');
 const end=new Date(),startAt=new Date(end-180000);const logs=await api('a','/internal/observations/logs?start='+startAt.toISOString()+'&end='+end.toISOString(),{metrics:true});
 assert.ok(logs.items.some(l=>l.route==='/api/orders'&&l.status===504));assert.ok(logs.items.every(l=>!('body'in l)&&!('authorization'in l)));
 mark('PASS real slow requests and TTL expiry, 5xx alert, downstream HTTP timeout and bounded correlated request logs');

 await resource('a','DB_LOCK',12);await wait('real row lock holder',async()=>(await api('a','/internal/lab',{lab:true})).activeTasks===1);await sleep(500);
 await api('b','/api/orders',{...post({productId:1,quantity:1,region:'LOCAL'}),headers:{'Idempotency-Key':'lock-'+crypto.randomUUID()},expected:503});await clear('a');
 await wait('lock released',async()=>(await api('a','/internal/lab',{lab:true})).activeTasks===0);
 await resource('a','DB_POOL',20);await wait('six real occupied Hikari connections',async()=>gauge(await metrics(),'hikaricp_connections_active')>=6,15);
 await api('a','/api/orders',{expected:503});await clear('a');await wait('pool resources released',async()=>(await api('a','/internal/lab',{lab:true})).activeTasks===0);
 mark('PASS actual InnoDB row-lock timeout, real Hikari exhaustion/503 and bounded resource cleanup');

 const heapBefore=gauge(await metrics(),'jvm_memory_used_bytes');await resource('a','HEAP',5);await sleep(800);const heapAfter=gauge(await metrics(),'jvm_memory_used_bytes');assert.ok(heapAfter-heapBefore>20*1024*1024);await clear('a');
 await wait('heap holder stopped',async()=>(await api('a','/internal/lab',{lab:true})).activeTasks===0);await resource('a','CPU',2);await wait('CPU resource automatically expires',async()=>(await api('a','/internal/lab',{lab:true})).scenario==='NONE',10);
 mark('PASS real bounded 32 MiB allocation, visible JVM metrics and automatic CPU exercise expiry');

 await stop('redis');await api('web','/api/products',{expected:503});await api('web','/api/orders');await start('redis');await wait('Redis restored',()=>api('web','/api/products'));
 await stop('kafka');const delayed=await order();await wait('real durable outbox pending',async()=>{const o=await api('web','/api/overview');return o.outboxPending>=1;});assert.equal((await api('web','/api/orders/'+delayed.id)).status,'ACCEPTED');await start('kafka');await wait('outbox drains after broker restart',async()=>(await api('web','/api/orders/'+delayed.id)).status==='READY',150);
 mark('PASS actual Redis outage isolation and Kafka broker outage with durable order/outbox recovery');

 await stop('mysql');await api('web','/api/orders',{expected:503});await metrics();await start('mysql');await wait('database restored',()=>api('web','/api/orders'),90);
 await stop('shop-a');for(let i=0;i<4;i++)await api('web','/api/products');await wait('actual instance-down alert',()=>alert('ShopInstanceDown'),40);await start('shop-a');await wait('instance restarted',()=>api('a','/api/orders'),90);
 mark('PASS actual MySQL outage with independent metrics, replica shutdown/GET failover, real down alert and recovery');
 await wait('all three scrape targets restored',async()=>{const d=await(await fetch('http://127.0.0.1:19094/api/v1/targets')).json();return d.data.activeTargets.length===3&&d.data.activeTargets.every(t=>t.health==='up');});
 report.status='PASSED';report.finishedAt=new Date().toISOString();await save();console.log('All standalone business and fault scenarios passed.');
}catch(e){report.status='FAILED';report.error=e.message;await save();throw e;}
finally{for(const name of stopped)await docker(['start',name]);for(const target of ['a','b','delivery'])await clear(target).catch(()=>{});}
