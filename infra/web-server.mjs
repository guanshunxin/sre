// 固定上游的小型静态资源/反向代理；不读取秘密、不重试 POST、不代理任意用户 URL。
import http from 'node:http';import {readFile} from 'node:fs/promises';
const targets=['http://shop-a:8080','http://shop-b:8080'];let next=0;
const headers=['authorization','content-type','idempotency-key','x-lab-token','x-request-id'];
function proxy(req,res,target,path,retry){
 const upstream=http.request(target+path,{method:req.method,headers:Object.fromEntries(headers.filter(k=>req.headers[k]).map(k=>[k,req.headers[k]])),timeout:8000},r=>{res.writeHead(r.statusCode,{'content-type':r.headers['content-type']||'application/json','cache-control':'no-store',...(r.headers['x-request-id']?{'x-request-id':r.headers['x-request-id']}:{})});r.pipe(res);});
 upstream.on('timeout',()=>upstream.destroy());
 upstream.on('error',()=>{if(res.headersSent){res.destroy();return;}if(retry){proxy(req,res,retry,path,null);return;}res.writeHead(503,{'content-type':'application/json'});res.end(JSON.stringify({code:'UPSTREAM_UNAVAILABLE',message:'业务实例暂时不可用；下单重试会复用原幂等键'}));});
 if(req.method==='GET')upstream.end();else{let bytes=0;req.on('data',b=>{bytes+=b.length;if(bytes>65536){upstream.destroy();req.destroy();}});req.pipe(upstream);}
}
http.createServer(async(req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname;
 if(pathname==='/health'){res.end('alive');return;}
 if(pathname.startsWith('/api/')){const index=next++%2;proxy(req,res,targets[index],req.url,req.method==='GET'?targets[1-index]:null);return;}
 const op=pathname.match(/^\/ops\/(a|b|delivery)\/(faults|lab)$/);
 if(op){proxy(req,res,op[1]==='delivery'?'http://delivery-api:8080':targets[op[1]==='a'?0:1],'/internal/'+op[2],null);return;}
 if(req.method!=='GET'){res.writeHead(405);res.end();return;}
 const asset=/^\/assets\/[a-zA-Z0-9_.-]+$/.test(pathname),file=asset?pathname:'/index.html';
 try{const data=await readFile('/app/dist'+file);res.writeHead(200,{'content-type':file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html; charset=utf-8','cache-control':asset?'public,max-age=31536000,immutable':'no-store'});res.end(data);}catch{res.writeHead(404);res.end();}
}).listen(8080,'0.0.0.0');
