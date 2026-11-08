/// <reference types="vitest/config" />
import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig(({ isSsrBuild }) => ({
  plugins: [
    tailwindcss(),
    !process.env.VITEST && reactRouter(),
  ],
  resolve: {
    tsconfigPaths: true,
  },
  server: {
    port: 3000,
    host: true,
  },
  build: {
    target: "esnext",
    sourcemap: false,
    chunkSizeWarningLimit: 600,
    rollupOptions: isSsrBuild
      ? undefined
      : {
          output: {
            manualChunks(id) {
              if (!id.includes("node_modules")) return undefined;
              if (id.includes("@tanstack/react-query")) return "vendor-query";
              if (id.includes("@dnd-kit/")) return "vendor-dnd";
              if (id.includes("yet-another-react-lightbox")) return "vendor-lightbox";
              if (
                id.includes("axios") ||
                id.includes("@stomp/stompjs") ||
                id.includes("sockjs-client")
              ) {
                return "vendor-network";
              }
              return undefined;
            },
          },
        },
  },
  test: {
    globals: true,
    environment: "jsdom",
    include: ["app/**/*.{test,spec}.{ts,tsx}"],
  },
}));
