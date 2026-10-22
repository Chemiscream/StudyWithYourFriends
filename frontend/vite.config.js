import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// 开发：npm run dev 后 Vite 起在 5173，/api 代理到 Spring Boot(8090)，改前端即时热更新。
// 预览新后端时可用 VITE_API_TARGET 指定代理目标，避免影响正式站。
// 发布：npm run build 产物直接输出到 Spring Boot 静态目录，随后 mvn package 打进 jar。
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      "/api": process.env.VITE_API_TARGET || "http://localhost:8090",
    },
  },
  build: {
    outDir: "../src/main/resources/static",
    emptyOutDir: true, // static 下旧 index.html 被产物取代；logo.svg 与 login.html 在 public/ 中自动拷入
  },
});
