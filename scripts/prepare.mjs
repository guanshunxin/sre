// 只初始化本项目；保留已有 .env 值，不读取父项目密钥。
import fs from 'node:fs/promises';import path from 'node:path';import {randomBytes} from 'node:crypto';import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));const file=path.join(root,'.env');
let content='';try{content=await fs.readFile(file,'utf8');}catch(e){if(e.code!=='ENOENT')throw e;}
const env=Object.fromEntries(content.split(/\r?\n/).filter(x=>x&&!x.trimStart().startsWith('#')).map(x=>{const i=x.indexOf('=');return[x.slice(0,i).trim(),x.slice(i+1).trim()];}));
const names=['SHOP_API_TOKEN','SHOP_METRICS_TOKEN','SHOP_LAB_TOKEN','MYSQL_ROOT_PASSWORD','MYSQL_PASSWORD','REDIS_PASSWORD'];
for(const key of names)if(!env[key]){env[key]=randomBytes(24).toString('hex');if(new RegExp('^'+key+'=','m').test(content))content=content.replace(new RegExp('^'+key+'=.*$','m'),key+'='+env[key]);else content+='\n'+key+'='+env[key];}
await fs.writeFile(file,content.trim()+'\n',{mode:0o600});await fs.mkdir(path.join(root,'.runtime'),{recursive:true,mode:0o700});await fs.chmod(path.join(root,'.runtime'),0o700);
async function write(name,values){for(const v of Object.values(values))if(/[\r\n]/.test(v))throw Error('Invalid multiline env');await fs.writeFile(path.join(root,'.runtime',name+'.env'),Object.entries(values).map(([k,v])=>k+'='+v).join('\n')+'\n',{mode:0o600});}
const access=Object.fromEntries(names.slice(0,3).map(k=>[k,env[k]]));
await write('shop',{...access,DB_URL:'jdbc:mysql://mysql:3306/shop?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true',DB_USER:'shop',DB_PASSWORD:env.MYSQL_PASSWORD,REDIS_PASSWORD:env.REDIS_PASSWORD});
await write('delivery',access);await write('mysql',{MYSQL_DATABASE:'shop',MYSQL_USER:'shop',MYSQL_PASSWORD:env.MYSQL_PASSWORD,MYSQL_ROOT_PASSWORD:env.MYSQL_ROOT_PASSWORD});await write('redis',{REDIS_PASSWORD:env.REDIS_PASSWORD});
await write('lab-overrides',{SHOP_PROFILE:'demo',LAB_ENABLED:'true'});
await fs.writeFile(path.join(root,'.runtime/metrics-token'),env.SHOP_METRICS_TOKEN,{mode:0o644});
console.log('Prepared independent project credentials; existing values preserved. Lab overrides are separate and opt-in.');
