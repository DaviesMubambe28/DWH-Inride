// Local preview only. Use a reviewed HTTPS static host for any public deployment.
import http from 'node:http';
import { createReadStream } from 'node:fs';
import { realpath, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=await realpath(fileURLToPath(new URL('../dist',import.meta.url)));
const types={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'text/javascript','.mjs':'text/javascript','.svg':'image/svg+xml','.jpg':'image/jpeg','.webp':'image/webp','.apk':'application/vnd.android.package-archive'};
const port=Number(process.env.LANDING_PREVIEW_PORT || 4173);
if(!Number.isInteger(port)||port<1024||port>65535)throw new Error('LANDING_PREVIEW_PORT must be 1024–65535');
http.createServer(async(req,res)=>{
 try{
  if(!['GET','HEAD'].includes(req.method)){res.writeHead(405);return res.end();}
  const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
  if(pathname.split('/').some(part=>part.startsWith('.'))){res.writeHead(404);return res.end();}
  const candidate=path.resolve(root,'.'+(pathname==='/'?'/index.html':pathname));
  const file=await realpath(candidate);
  if(!file.startsWith(root+path.sep)){res.writeHead(404);return res.end();}
  const info=await stat(file);if(!info.isFile()){res.writeHead(404);return res.end();}
  res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');
  res.setHeader('Content-Length',info.size);res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Cache-Control','no-cache');
  if(path.extname(file)==='.apk')res.setHeader('Content-Disposition','attachment');
  if(req.method==='HEAD')return res.end();
  createReadStream(file).on('error',()=>res.destroy()).pipe(res);
 }catch{res.writeHead(404);res.end('Not found');}
}).listen(port,'127.0.0.1',()=>console.log(`Local preview: http://127.0.0.1:${port}/ (not a public deployment)`));
