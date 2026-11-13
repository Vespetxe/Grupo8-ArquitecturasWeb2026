'use strict';
const {spawnSync}=require('node:child_process');
const path=require('node:path');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../..');
const projects=[['api-gateway','gateway'],['EventoCatalogo_Service','eventos'],['inventarioReserva_service','inventario'],['entradas_service','entradas'],['comprapago_service','compras']];
if(require.main===module){
  assert.equal(process.argv.length,2,'No se admiten argumentos');
  for(const [directory,tag] of projects){
    const context=path.join(root,'TicketYa',directory);
    console.log(`Construyendo ticketya-e2-${tag}:dev; contexto=${context}`);
    const r=spawnSync('docker',['build','--progress=plain','--tag',`ticketya-e2-${tag}:dev`,'.'],{cwd:context,stdio:'inherit',shell:false});
    if(r.error||r.status!==0){console.error('Construccion fallida; no se continua ni se modifican servicios.');process.exit(1);}
  }
}
