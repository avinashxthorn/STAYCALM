import{env}from'cloudflare:workers';
export function GET(){return Response.json({service:'Staycalm AI',ready:!!env.STAYCALM_AI_API_KEY&&!!env.DB},{headers:{'Cache-Control':'no-store'}});}
