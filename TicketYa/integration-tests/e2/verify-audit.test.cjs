'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {collect,contract}=require('./verify-audit.cjs');
function fixture(){
 const correlationId='12345678-1234-4123-8123-123456789012',trace={id:'a'.repeat(32)};
 const row={eventId:'12345678-1234-4123-8123-123456789013',eventType:'AUDIT',timestamp:new Date().toISOString(),service:{name:'compras'},message:'Audit event',action:'PURCHASE_CREATED',auditScope:'LOCAL_TRANSACTION',actorType:'CLIENTE',actorVerified:true,actorId:'42',resourceType:'PURCHASE',resourceId:'1',operationId:'PURCHASE:1',outcome:'SUCCESS',correlationId,trace};
 return {rows:[row],http:[{component:'compras',actorType:'USER',correlationId,trace}]};
}
function check(f){f.rows.forEach(r=>r.component='compras');return contract(f);}
test('accepts a correlated audit event with verified actor and stable operation',()=>assert.equal(check(fixture()),true));
test('rejects duplicate event identifiers',()=>{const f=fixture();f.rows.push({...f.rows[0]});assert.throws(()=>check(f));});
test('rejects attribution to an unauthenticated caller',()=>{const f=fixture();f.rows[0].actorVerified=false;assert.throws(()=>check(f));});
test('rejects secrets outside audit records too',()=>assert.throws(()=>collect({compras:'secret-sentinel'},['secret-sentinel'])));
test('rejects JWT in technical logs',()=>assert.throws(()=>collect({compras:'eyJhbGciOiJSUzI1NiJ9.payload.signature'})));
test('rejects divergent HTTP and audit traces',()=>{const f=fixture();f.rows[0].trace={id:'b'.repeat(32)};assert.throws(()=>check(f));});
test('rejects a completed operation attributed to a denied HTTP attempt',()=>{const f=fixture();f.rows[0].action='ACCESS_ATTEMPT';assert.throws(()=>check(f));});
test('rejects operation IDs derived from correlation metadata',()=>{const f=fixture();f.rows[0].operationId=f.rows[0].correlationId;assert.throws(()=>check(f));});
test('rejects missing committed resource identity',()=>{const f=fixture();delete f.rows[0].resourceId;assert.throws(()=>check(f));});
test('requires controlled error codes on rejected HTTP attempts',()=>{const f=fixture();Object.assign(f.rows[0],{action:'ACCESS_ATTEMPT',resourceType:'HTTP',outcome:'REJECTED'});delete f.rows[0].resourceId;delete f.rows[0].operationId;assert.throws(()=>check(f));f.rows[0].errorCode='HTTP_REJECTED';assert.equal(check(f),true);});
