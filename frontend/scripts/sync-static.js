// 把构建产物同步到外部静态目录 static-frontend/（Spring Boot 优先读取，改前端免重启生效）
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const src = path.resolve(here, "..", "..", "src", "main", "resources", "static");
const dest = path.resolve(here, "..", "..", "static-frontend");

if (!fs.existsSync(src)) {
  console.error("sync-static: 源目录不存在 " + src);
  process.exit(1);
}
fs.rmSync(dest, { recursive: true, force: true });
fs.cpSync(src, dest, { recursive: true });
console.log("sync-static: 已同步 -> " + dest);
