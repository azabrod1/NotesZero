import { execFileSync } from "child_process";
import { resolve } from "path";

process.chdir(resolve(import.meta.dirname, "frontend"));
execFileSync(process.execPath, ["node_modules/vite/bin/vite.js", "--host", "127.0.0.1", "--port", "3000"], { stdio: "inherit" });
