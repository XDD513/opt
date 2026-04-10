#!/usr/bin/env node
/**
 * 一键：1000用户全流程（batch-fullflow-users）→ acceptance-tests 生成最终图表/报告。
 * 说明：全流程耗时较长；可先用 --count=10 验证环境。
 *
 * 用法：
 *   node run-pipeline.js
 *
 * 依赖：已配置 acceptance-tests/.env（TEST_USERNAME 等）；stress-load 使用同一账号。
 */
/* eslint-disable no-console */
const { spawnSync } = require("child_process");
const path = require("path");

const root = __dirname;
const acceptanceDir = path.join(root, "..", "acceptance-tests");

function run(cmd, args, cwd) {
  console.log(`\n>>> ${cmd} ${args.join(" ")} (cwd=${cwd})\n`);
  const r = spawnSync(cmd, args, { cwd, stdio: "inherit", shell: false });
  if (r.status !== 0) {
    console.error(`Exit ${r.status}`);
    process.exit(r.status || 1);
  }
}

// 1) 先跑 1000 用户全流程（真正的“1000用户同时完整流程抗压”由 concurrency 决定）
run(process.execPath, ["batch-fullflow-users.js"], root);

// 2) 生成验收报告（会自动读取 batch-fullflow/output/batch-fullflow-result.json 并出图表）
run(process.execPath, ["run.mjs"], acceptanceDir);
console.log("\nPipeline done: batch fullflow + acceptance reports generated.\n");
