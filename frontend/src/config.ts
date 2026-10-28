/**
 * Demo mode is ON only in builds made with `--mode demo` (`npm run build:demo`, `npm run dev:demo`).
 * Vite replaces `import.meta.env.MODE` at build time, so the normal build drops the mock API entirely.
 * No env file is involved, so the flag works the same on every OS and on Vercel.
 */
export const DEMO_MODE = import.meta.env.MODE === 'demo'
