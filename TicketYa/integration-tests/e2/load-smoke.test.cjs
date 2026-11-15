'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{summarize}=require('./load-smoke.cjs');
test('carga: separa éxitos, conflictos y saturación de errores inesperados',()=>{const r=summarize([{status:201,durationMs:10},{status:409,durationMs:20},{status:503,durationMs:30},{status:500,durationMs:40}]);assert.equal(r.successes,1);assert.equal(r.conflicts409,1);assert.equal(r.technicalRejections,1);assert.equal(r.unexpectedErrors,1);assert.deepEqual(r.latencyMs,{p50:20,p95:40,max:40});});
test('carga: sin muestras no inventa latencias',()=>assert.equal(summarize([]).latencyMs.p95,null));
test('carga: un rechazo de propiedad esperado no es un error inesperado',()=>{const r=summarize([{status:404,expected:404,durationMs:8}]);assert.equal(r.expectedRejections,1);assert.equal(r.unexpectedErrors,0);});
