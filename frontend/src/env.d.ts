/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "true" only in the demo build (`npm run build:demo`, see .env.demo). */
  readonly VITE_DEMO_MODE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
