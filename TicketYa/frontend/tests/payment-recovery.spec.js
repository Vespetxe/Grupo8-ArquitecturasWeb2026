import {test,expect} from '@playwright/test';
import fs from 'node:fs';
test('Recuperación explícita de pago incierto conserva intento y no se repite automáticamente',async({page})=>{
 const data=JSON.parse(fs.readFileSync(process.env.TY_PAYMENT_RECOVERY_INPUT,'utf8'));
 expect(data.project).toMatch(/^ticketya-e2-reg-[a-f0-9]{16}$/);expect(data.port).toBe(Number(process.env.TY_DELIVERY_PORT));
 let posts=0;page.on('request',r=>{if(r.method()==='POST'&&r.url().endsWith('/pagos'))posts++;});
 await page.goto('/#/login');await page.getByLabel('Correo electrónico').fill(data.client.email);
 try{await page.getByLabel('Contraseña',{exact:true}).fill(data.client.password);}catch{throw Error('Private credential input failed');}
 await page.getByRole('button',{name:'Ingresar',exact:true}).click();
 await expect(page.getByRole('link',{name:'Compras',exact:true})).toBeVisible();
 await page.goto('/#/purchase/'+data.purchase);
 const recover=page.getByRole('button',{name:'Recuperar pago original',exact:true});await expect(recover).toBeVisible();
 expect(posts).toBe(0);
 await recover.click();await expect(page.getByText('COMPLETADA',{exact:true})).toBeVisible();
 expect(posts).toBe(1);await expect(recover).toHaveCount(0);
 await page.getByRole('button',{name:'Consultar estado actual'}).click();
 await expect(page.getByText('COMPLETADA',{exact:true})).toBeVisible();expect(posts).toBe(1);
});
