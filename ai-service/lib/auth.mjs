import{CoachError}from'./coach.mjs';
let cached=null,expires=0,lastReload=0;
const unauthorized=()=>{throw new CoachError(401,'Sign in to your verified Staycalm account to use Staycalm AI.');};
function decode(part){try{return JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(part.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0))));}catch{unauthorized();}}
export async function verifyToken(token,project,keys,now=Date.now()){
 if(typeof token!=='string'||token.length>12000)unauthorized();const parts=token.split('.');if(parts.length!==3)unauthorized();const head=decode(parts[0]),payload=decode(parts[1]);if(head.alg!=='RS256'||typeof head.kid!=='string')unauthorized();const key=keys.find(k=>k.kid===head.kid&&k.kty==='RSA');if(!key)unauthorized();const seconds=Math.floor(now/1000);
 if(payload.aud!==project||payload.iss!==`https://securetoken.google.com/${project}`||typeof payload.sub!=='string'||!payload.sub||payload.sub.length>128||payload.email_verified!==true||!Number.isInteger(payload.exp)||payload.exp<=seconds||!Number.isInteger(payload.iat)||payload.iat>seconds+30||!Number.isInteger(payload.auth_time)||payload.auth_time>seconds+30)unauthorized();
 try{const publicKey=await crypto.subtle.importKey('jwk',key,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']),signature=Uint8Array.from(atob(parts[2].replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));if(!await crypto.subtle.verify('RSASSA-PKCS1-v1_5',publicKey,signature,new TextEncoder().encode(parts[0]+'.'+parts[1])))unauthorized();return payload.sub;}catch{unauthorized();}
}
export async function authenticate(request,project,fetcher=fetch){
 const header=request.headers.get('authorization')||'';if(!header.startsWith('Bearer '))unauthorized();const token=header.slice(7);if(token.length>12000)unauthorized();const now=Date.now(),kid=decode(token.split('.')[0]).kid;
 if(!cached||now>=expires||(!cached.some(k=>k.kid===kid)&&now-lastReload>60000)){lastReload=now;const r=await fetcher('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com',{signal:AbortSignal.timeout(10000)});if(!r.ok)throw new CoachError(503,'Staycalm AI could not verify sign-in. Try again later.');const data=await r.json();if(!Array.isArray(data.keys)||!data.keys.length)throw new CoachError(503,'Staycalm AI sign-in verification is unavailable.');cached=data.keys;expires=now+Math.min(3600,Number(r.headers.get('cache-control')?.match(/max-age=(\d+)/)?.[1]||300))*1000;}
 return verifyToken(token,project,cached,now);
}
