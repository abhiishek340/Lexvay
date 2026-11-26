// Proxy for the end-to-end stack; the backend port follows the slot chosen by scripts/e2e.sh.
const target = `http://localhost:${process.env.LEXVAY_E2E_BACKEND_PORT ?? '8081'}`;

export default {
  '/api': { target, secure: false, changeOrigin: false },
  '/actuator': { target, secure: false, changeOrigin: false },
};
