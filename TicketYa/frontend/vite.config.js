import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env };
  const target = new URL(env.TICKETYA_GATEWAY_URL || 'http://127.0.0.1:48080');
  if (!['http:', 'https:'].includes(target.protocol) || target.username || target.password) throw new Error('Gateway URL inválida');
  const proxy = { '/gateway': { target: target.origin, changeOrigin: true, rewrite: path => path.replace(/^\/gateway/, ''), timeout: 35000, proxyTimeout: 35000 } };
  return { server: { port: 5173, strictPort: true, proxy }, preview: { port: 5173, strictPort: true, proxy } };
});
