import { defineConfig } from '@playwright/test';
const port=Number(process.env.TY_DELIVERY_PORT);
if(!Number.isInteger(port)||port<48080||port>48089)throw Error('Explicit isolated Gateway port required');
export default defineConfig({testDir:'./tests',testMatch:process.env.TY_PAYMENT_RECOVERY_INPUT?'payment-recovery.spec.js':'delivery.spec.js',workers:1,retries:0,timeout:30000,reporter:'line',outputDir:'../integration-tests/target/delivery/browser',use:{baseURL:'http://127.0.0.1:5174',channel:process.env.TY_BROWSER_CHANNEL||'msedge',headless:true,trace:'off',screenshot:'off'},webServer:{command:'npm run dev -- --port 5174',url:'http://127.0.0.1:5174',reuseExistingServer:false,timeout:30000,env:{TICKETYA_GATEWAY_URL:`http://127.0.0.1:${port}`}}});
