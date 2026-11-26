'use strict';
// Executed only inside the isolated network. Tokens remain in memory/stdin.
let input='';process.stdin.setEncoding('utf8');process.stdin.on('data',part=>input+=part);
process.stdin.on('end',()=>{
 const {collection,environment,bail=false}=JSON.parse(input);
 const newman=require('/tools/node_modules/newman');
 let runtimeVariables;
 const execution=newman.run({collection,environment,bail,reporters:[],ignoreRedirects:true,timeoutRequest:10000,timeoutScript:5000},(error,summary)=>{
  if(error){console.error('Newman network execution failed');process.exitCode=1;return;}
  const results=summary.run.executions.map(e=>({name:e.item.name,method:e.request.method,path:e.request.url.getPath(),status:e.response?.code,assertions:e.assertions?.map(a=>({name:a.assertion,passed:!a.error}))}));
  const privateCollection=summary.collection.toJSON();if(runtimeVariables)privateCollection.variable=runtimeVariables;
  require('node:fs').writeFileSync('/tmp/newman-private-context.json',JSON.stringify({collection:privateCollection,environment:summary.environment.toJSON()}),{mode:0o600});
  const report={requests:results.length,assertions:summary.run.stats.assertions.total,failures:summary.run.failures.map(f=>({name:f.source?.name,test:f.error?.test,message:f.error?.message})),results};
  console.log(JSON.stringify(report));if(report.failures.length)process.exitCode=1;
 });
 execution.on('script',(_,args)=>{const scope=args.execution?.collectionVariables;if(scope){const json=typeof scope.toJSON==='function'?scope.toJSON():scope;if(Array.isArray(json.values))runtimeVariables=json.values;}});
});
