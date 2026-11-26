'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
module.exports=function run({meta,docker,adminPassword,provisioned,sql,suffix="",includeAdditional=false}){
 const flow=require('./postman-flows.cjs');
 const collection={info:{name:'TicketYa — flujo autónomo Postman/OpenFeign',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},event:[flow.setup()],item:[{name:'Flujo completo con IDs dinámicos',item:flow.items()}]};
 const values=[{key:'gatewayUrl',value:'http://gateway:8080'},{key:'administradorEmail',value:'admin-'+meta.id+'@example.test'},{key:'administradorPassword',value:adminPassword}];
 for(const role of ['ORGANIZADOR','PERSONAL_ACCESO']){const user=provisioned.find(p=>p.role===role);assert(user);values.push({key:role.toLowerCase()+'Email',value:user.email},{key:role.toLowerCase()+'Password',value:user.password});}
 if(includeAdditional){const caller=JSON.parse(docker(['inspect',meta.project+'-compras-1']))[0];assert.equal(caller.Config.Labels['com.docker.compose.project'],meta.project);const env=Object.fromEntries(caller.Config.Env.map(v=>[v.slice(0,v.indexOf('=')),v.slice(v.indexOf('=')+1)]));for(const target of ['inventario','entradas'])values.push({key:'secret_compras_'+target,value:env['TY_SECURITY_'+target.toUpperCase()+'_SECRET']});collection.item.push({name:'Complementarios sin nuevos cobros',item:require('./postman-additional.cjs').items()});}
 let output;try{output=docker(['run','--interactive','--name',meta.project+'-newman-flows'+suffix,'--network',meta.network,'--mount',`type=bind,source=${path.resolve(__dirname,'../target/openfeign/newman/node_modules')},target=/tools/node_modules,readonly`,'--mount',`type=bind,source=${path.join(__dirname,'newman-network.cjs')},target=/network.cjs,readonly`,'node:22.21.1-alpine','node','/network.cjs'],JSON.stringify({collection,environment:{values},bail:true}),{},180000);}catch(error){if(error.stdout)fs.writeFileSync(path.join(meta.directory,'newman-flows-results.json'),String(error.stdout));throw error;}
 const report=JSON.parse(output);fs.writeFileSync(path.join(meta.directory,'newman-flows-results.json'),JSON.stringify(report,null,2));assert.deepEqual(report.failures,[]);
 // Constraints independent of the number of scenarios: one payment and one emission batch per purchase.
 for(const [table,key,schema]of [['pagos','compra_id','compras'],['ticket_batches','order_id','entradas']])assert.equal(sql(`SELECT COUNT(*) FROM (SELECT ${key} FROM ${meta.schemas[schema]}.${table} GROUP BY ${key} HAVING COUNT(*)>1) duplicates;`),'0');
 report.noDuplicatePaymentsOrBatches=true;
 fs.writeFileSync(path.join(meta.directory,'newman-flows-results.json'),JSON.stringify(report,null,2));return report;
};
