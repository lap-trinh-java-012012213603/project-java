/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  readonly VITE_WS_ENDPOINT: string;
  readonly VITE_WS_BASE_URL?: string;
  readonly VITE_STORAGE_PUBLIC_URL: string;
  readonly VITE_GOOGLE_CLIENT_ID?: string;
  readonly VITE_APP_ENV: "development" | "staging" | "production";
  readonly VITE_DEFAULT_LAT: string;
  readonly VITE_DEFAULT_LNG: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
