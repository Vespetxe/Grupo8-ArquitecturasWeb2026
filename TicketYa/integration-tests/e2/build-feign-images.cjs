'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../..');
const projects={gateway:'api-gateway',identidad:'identidad_service',eventos:'EventoCatalogo_Service',inventario:'inventarioReserva_service',compras:'comprapago_service',entradas:'entradas_service'};
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name)):[path.join(dir,e.name)]);}
function fingerprint(dir){const hash=crypto.createHash('sha256');for(const file of ['pom.xml','Dockerfile','.dockerignore',...files(path.join(dir,'src/main')).map(f=>path.relative(dir,f))].sort()){hash.update(file.replaceAll('\\','/'));hash.update(fs.readFileSync(path.join(dir,file)));}return hash.digest('hex');}
function main(){
 assert.equal(process.argv.length,2,'No overrides: this builder cannot tag demo/dev images');
 const hashes=Object.fromEntries(Object.entries(projects).map(([name,dir])=>[name,fingerprint(path.join(root,'TicketYa',dir))]));
 const tag='feign-'+crypto.createHash('sha256').update(JSON.stringify(hashes)).digest('hex').slice(0,8),images={};
 for(const [name,dir]of Object.entries(projects)){
  const image='ticketya-e2-'+name+':'+tag;let current;try{current=JSON.parse(cp.execFileSync('docker',['image','inspect',image],{encoding:'utf8',stdio:['ignore','pipe','pipe'],timeout:20000}))[0];}catch{}
  if(current){assert.equal(current.Config.Labels?.['org.ticketya.source-sha256'],hashes[name],'Existing tag has different/unattested source; preserve it and investigate');console.log('Reutilizando '+image);}
  else cp.execFileSync('docker',['build','--progress=plain','--label','org.ticketya.source-sha256='+hashes[name],'--tag',image,path.join(root,'TicketYa',dir)],{stdio:'inherit',timeout:900000});
  images[name]=JSON.parse(cp.execFileSync('docker',['image','inspect',image],{encoding:'utf8',timeout:20000}))[0].Id;
 }
 const output=path.join(__dirname,'../target/openfeign');fs.mkdirSync(output,{recursive:true});const result={tag,sourceHashes:hashes,images};fs.writeFileSync(path.join(output,'images.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({tag}));
}
if(require.main===module)main();module.exports={fingerprint};
