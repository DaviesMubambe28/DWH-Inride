type ObjectEntry={name:string;id:string|null};
type List=(prefix:string,offset:number)=>Promise<ObjectEntry[]>;
export async function collectPrivateObjects(userId:string,list:List,limit=100) {
 if(!/^[a-f0-9-]{36}$/.test(userId))throw Error('INVALID_SUBJECT');
 const paths:string[]=[];const folders=[userId];let pages=0;
 while(folders.length){
  const folder=folders.shift()!;
  for(let offset=0;;offset+=100){
   if(++pages>10)return {paths,complete:false};
   const entries=await list(folder,offset);
   for(const entry of entries){
    if(!entry.name || entry.name==='.' || entry.name==='..' || /[\\/\u0000]/.test(entry.name))throw Error('UNSAFE_OBJECT');
    const path=folder+'/'+entry.name;
    if(path.split('/').length>12)throw Error('OBJECT_DEPTH_LIMIT');
    if(entry.id===null)folders.push(path);else paths.push(path);
    if(paths.length>=limit)return {paths,complete:false};
   }
   if(entries.length<100)break;
  }
 }
 return {paths,complete:true};
}
export type ErasureJob={user_id:string;lease:string;state:string};
type Ports={list:List;remove:(paths:string[])=>Promise<unknown>;finalize:()=>Promise<boolean>;deleteIdentity:()=>Promise<unknown>;complete:()=>Promise<boolean>};
export async function eraseAccount(job:ErasureJob,ports:Ports) {
 const batch=await collectPrivateObjects(job.user_id,ports.list);
 for(let i=0;i<batch.paths.length;i+=100)await ports.remove(batch.paths.slice(i,i+100));
 if(!batch.complete)return 'pending';
 if(!await ports.finalize())throw Error('ERASURE_NOT_FINALIZED');
 await ports.deleteIdentity(); // Auth must remain recoverable until row/storage work succeeds.
 if(!await ports.complete())throw Error('ERASURE_NOT_COMPLETED');
 return 'erased';
}
