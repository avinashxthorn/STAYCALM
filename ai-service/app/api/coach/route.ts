import { env } from 'cloudflare:workers';
import { authenticate } from '@/lib/auth.mjs';
import { boundedText, CoachError, fingerprint, generateReport, validateContext } from '@/lib/coach.mjs';
export const dynamic='force-dynamic';
const json=(body:unknown,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
export async function POST(request:Request){
 try{
  const uid=await authenticate(request,env.FIREBASE_PROJECT_ID||'staycalm-aefb7');if(!env.DB)throw new CoachError(503,'Staycalm AI is being connected. Try again later.');
  const envelope=JSON.parse(await boundedText(request));if(typeof envelope.context!=='string'||envelope.context.length>24000)throw new CoachError(400,'Focus details are too large.');
  const context=validateContext(envelope.context),hash=await fingerprint(envelope.context),owner=await fingerprint(uid),now=Date.now(),day=new Date(now).toISOString().slice(0,10);
  const cached=await env.DB.prepare('SELECT payload,fingerprint,created_at FROM coach_cache WHERE owner=?').bind(owner).first<{payload:string;fingerprint:string;created_at:number}>();
  if(cached&&cached.fingerprint===hash&&now-cached.created_at<6*3600000)return json(JSON.parse(cached.payload));
  const counters=await env.DB.batch([
   env.DB.prepare('INSERT INTO coach_quota(id,count,next_at) VALUES(?,1,?) ON CONFLICT(id) DO UPDATE SET count=count+1,next_at=excluded.next_at WHERE coach_quota.count<6 AND coach_quota.next_at<=? RETURNING count').bind(owner+':'+day,now+60000,now),
   env.DB.prepare('INSERT INTO coach_quota(id,count,next_at) VALUES(?,1,0) ON CONFLICT(id) DO UPDATE SET count=count+1 WHERE coach_quota.count<50 RETURNING count').bind('global:'+day)
  ]);
  if(counters.some(r=>!r.results.length))throw new CoachError(429,'Staycalm AI is taking a short pause. Your saved suggestions are still available.');
  const report=await generateReport(context,env),result={report,generatedAt:now,contextHash:hash};
  await env.DB.prepare('INSERT INTO coach_cache(owner,payload,fingerprint,created_at) VALUES(?,?,?,?) ON CONFLICT(owner) DO UPDATE SET payload=excluded.payload,fingerprint=excluded.fingerprint,created_at=excluded.created_at').bind(owner,JSON.stringify(result),hash,now).run();
  await env.DB.batch([env.DB.prepare('DELETE FROM coach_cache WHERE created_at<?').bind(now-7*86400000),env.DB.prepare('DELETE FROM coach_quota WHERE id NOT LIKE ?').bind('%:'+day)]);
  return json(result);
 }catch(error){if(error instanceof CoachError)return json({error:error.message},error.status);return json({error:'Staycalm AI is temporarily unavailable. Your saved suggestions are kept.'},503);}
}
export async function DELETE(request:Request){try{const uid=await authenticate(request,env.FIREBASE_PROJECT_ID||'staycalm-aefb7');if(!env.DB)throw new CoachError(503,'Staycalm AI is temporarily unavailable.');await env.DB.prepare('DELETE FROM coach_cache WHERE owner=?').bind(await fingerprint(uid)).run();return json({deleted:true});}catch(error){return json({error:error instanceof CoachError?error.message:'Staycalm AI is temporarily unavailable.'},error instanceof CoachError?error.status:503);}}
