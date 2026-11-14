'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {verifySaturation,unchanged}=require('./fault-campaign.cjs');
const accepted={status:200,body:[]},rejected={status:503,correlationId:'one',body:{code:'ORDERS_UNAVAILABLE'}};
test('Overload requires exact technical rejection evidence',()=>assert.equal(verifySaturation([accepted,rejected],[{eventType:'DEPENDENCY_READ',errorCategory:'BULKHEAD_FULL',correlationId:'one'}]).technicalRejections,1));
test('An arbitrary 503 cannot pass as expected saturation',()=>assert.throws(()=>verifySaturation([accepted,rejected],[])));
test('Reducing the burst to all 200 is not saturation evidence',()=>assert.throws(()=>verifySaturation([accepted],[])));
test('Financial or identifier changes fail fault validation',()=>{unchanged(['same'],['same']);assert.throws(()=>unchanged(['before'],['after']));});
