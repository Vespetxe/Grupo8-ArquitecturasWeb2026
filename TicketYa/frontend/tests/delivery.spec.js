import {test,expect} from '@playwright/test';
import fs from 'node:fs';
test('QR real: personal asignado acepta una vez y rechaza repetición e inválido',async({page})=>{
 const data=JSON.parse(fs.readFileSync(process.env.TY_DELIVERY_INPUT,'utf8'));
 expect(data.project).toMatch(/^ticketya-e2-reg-[a-f0-9]{16}$/);expect(data.port).toBe(Number(process.env.TY_DELIVERY_PORT));
 await page.goto('/#/login');await page.getByLabel('Correo electrónico').fill(data.staff.email);
 try{await page.getByLabel('Contraseña',{exact:true}).fill(data.staff.password);}catch{throw Error('Private credential input failed');}
 await page.getByRole('button',{name:'Ingresar',exact:true}).click();await page.getByRole('link',{name:'Control de acceso'}).click();await page.getByLabel('ID de evento',{exact:true}).fill(String(data.event));
 try{await page.getByLabel('Token QR',{exact:true}).fill(data.qr);}catch{throw Error('Private QR input failed');}
 await page.getByRole('button',{name:'Validar entrada'}).click();await expect(page.getByRole('status')).toContainText('Ingreso aceptado');
 const repeat=page.waitForResponse(r=>r.url().endsWith('/validaciones')&&r.request().method()==='POST');await page.getByRole('button',{name:'Validar entrada'}).click();expect((await repeat).status()).toBe(409);await expect(page.getByRole('alert')).toBeVisible();
 await page.getByLabel('Token QR',{exact:true}).fill('invalid');const invalid=page.waitForResponse(r=>r.url().endsWith('/validaciones')&&r.request().method()==='POST');await page.getByRole('button',{name:'Validar entrada'}).click();expect((await invalid).status()).toBe(400);await expect(page.getByRole('alert')).toBeVisible();
});
