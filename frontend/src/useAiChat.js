// 看板娘「库莉姆」问答：历史记忆、排队轮询、SSE 流式渲染
// 默认使用服务端本地模型；用户可在「设置 → AI 设置」中切换外部 API 或指定模型（设置仅存本浏览器）
import { reactive } from "vue";
import { request, authHeaders, getToken } from "./api.js";
import { mdRender } from "./utils/md.js";

const defCfg = { provider: "local", base_url: "", api_key: "", api_model: "", local_model: "" };
function loadCfg() {
  try { return Object.assign({ ...defCfg }, JSON.parse(localStorage.getItem("studylog_ai_cfg") || "{}")); }
  catch (e) { return { ...defCfg }; }
}

export const ai = reactive({
  history: [],       // {role, content}
  busy: false,
  status: "",
  statusErr: false,
  model: "",
  streaming: "",     // 正在生成的回答（逐字增长）
  streamError: "",   // 流式过程中/建立前的确定性错误
  models: [],        // 本地可用模型列表
  cfg: loadCfg(),
  loaded: false,     // 本浏览器是否已加载过历史
});

export function mdOf(text) { return mdRender(text); }

function saveCfg() { localStorage.setItem("studylog_ai_cfg", JSON.stringify(ai.cfg)); }

// 发给后端的模型配置：外部模式带上 URL/Key/模型名；本地模式只带所选模型（可空=自动）
export function aiSettingsPayload() {
  if (ai.cfg.provider === "api") {
    return { base_url: ai.cfg.base_url, api_key: ai.cfg.api_key, model: ai.cfg.api_model };
  }
  return ai.cfg.local_model ? { model: ai.cfg.local_model } : {};
}

export function setStatus(text, isErr) {
  ai.status = text || "";
  ai.statusErr = !!isErr;
}

export async function loadHistory() {
  try {
    const res = await request("/api/ai/history");
    if (res.ok) ai.history = res.messages || [];
  } catch (e) { /* 历史加载失败不影响主功能 */ }
}

let statusTimer = null;

function pollStatus() {
  stopPoll();
  statusTimer = setInterval(async () => {
    if (!ai.busy) { stopPoll(); return; }
    try {
      const s = await request("/api/ai/status");
      if (!s.ok) return;
      const ahead = (s.running || 0) + (s.waiting || 0) - 1; // 减去自己
      setStatus(ahead > 0 ? `排队中（前面还有约 ${ahead} 人）…` : "库莉姆正在奋笔疾书…");
    } catch (e) { /* 轮询失败忽略 */ }
  }, 2000);
}
function stopPoll() {
  if (statusTimer) { clearInterval(statusTimer); statusTimer = null; }
}

export async function send(q) {
  if (!q || ai.busy) return null;
  if (ai.cfg.provider === "api" && (!ai.cfg.base_url || !ai.cfg.api_model)) {
    setStatus("请先在「设置 → AI 设置」中填写 Base URL 和模型名并保存", true);
    return null;
  }
  ai.busy = true;
  ai.streaming = "";   // 清理上一轮的半截内容
  ai.streamError = ""; // 清理上一轮的错误气泡
  ai.history.push({ role: "user", content: q });
  setStatus("排队中…");
  pollStatus();
  let doneOk = false;
  try {
    // 流式请求必须用原生 fetch（request() 只解析 JSON）
    const res = await fetch("/api/ai/chat", {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json" }, authHeaders()),
      body: JSON.stringify({ question: q, settings: aiSettingsPayload() }),
    });
    const ctype = res.headers.get("Content-Type") || "";
    if (!res.ok || !ctype.includes("text/event-stream")) {
      // 确定性错误（排队满/未登录/模型不可达等）：服务端返回 JSON
      let msg = res.ok ? "响应格式异常，请刷新重试" : "请求失败，请稍后重试";
      try { const j = await res.json(); if (j.message) msg = j.message; } catch (e) { /* 忽略 */ }
      ai.streamError = msg;
      setStatus("");
      return null;
    }
    // 流式：排队已在流建立前结束，停止轮询
    stopPoll();
    setStatus("库莉姆正在奋笔疾书…");
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buf = "", full = "", model = "", errored = false, done = false;
    for (;;) {
      const { value, done: fin } = await reader.read();
      if (fin) break;
      buf += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buf.indexOf("\n")) >= 0) {
        const line = buf.slice(0, idx).trim();
        buf = buf.slice(idx + 1);
        if (!line.startsWith("data:")) continue;
        try {
          const ev = JSON.parse(line.slice(5).trim());
          if (ev.delta) { full += ev.delta; ai.streaming = full; }
          if (ev.model) model = ev.model;
          if (ev.error) { errored = true; full += (full ? "\n\n" : "") + "⚠️ " + ev.error; ai.streaming = full; }
          if (ev.done) done = true;
        } catch (e) { /* 单行解析失败忽略 */ }
      }
    }
    if (done && !errored) {
      doneOk = true;
      ai.model = model;
      ai.history.push({ role: "assistant", content: full });
      setStatus("");
    } else if (!full) {
      ai.streamError = "连接中断，请稍后重试";
      setStatus("");
    } else {
      // 半截内容但没收到 done：保留已生成内容但不写入历史
      setStatus("");
    }
    return doneOk ? full : null;
  } catch (e) {
    ai.streamError = "网络错误，请稍后重试";
    setStatus("");
    return null;
  } finally {
    ai.busy = false;
    stopPoll();
    if (doneOk) {
      ai.streaming = "";
      ai.streamError = "";
    }
  }
}

export async function clearHistory() {
  const res = await request("/api/ai/history", { method: "DELETE" });
  if (res.ok) {
    ai.history = [];
    ai.streaming = "";
    ai.streamError = "";
    setStatus("对话已清空");
    return { ok: true };
  }
  return { ok: false, message: res.message || "清空失败" };
}

export async function loadModels() {
  try {
    const res = await request("/api/ai/models", { method: "POST", body: JSON.stringify({ settings: {} }) });
    if (res.ok && res.models) {
      ai.models = res.models;
      // 已保存的模型不在列表里时回退「自动」
      if (ai.cfg.local_model && !res.models.includes(ai.cfg.local_model)) {
        ai.cfg.local_model = "";
        saveCfg();
      }
    }
  } catch (e) { /* 模型列表加载失败时保持「自动」 */ }
}

export function saveAiCfgFields(base_url, api_key, api_model) {
  ai.cfg.base_url = (base_url || "").trim();
  ai.cfg.api_key = (api_key || "").trim();
  ai.cfg.api_model = (api_model || "").trim();
  saveCfg();
  setStatus("AI 设置已保存");
}

export function setProvider(p) {
  ai.cfg.provider = p;
  saveCfg();
}

export function setLocalModel(m) {
  ai.cfg.local_model = m;
  saveCfg();
}

// 登录后加载一次历史（logout 时由 store 调 resetAi 复位）
export async function ensureAiLoaded() {
  if (ai.loaded || !getToken()) return;
  ai.loaded = true;
  await loadHistory();
  if (ai.cfg.provider === "local") loadModels();
}

export function resetAi() {
  ai.history = [];
  ai.busy = false;
  ai.streaming = "";
  ai.streamError = "";
  ai.status = "";
  stopPoll();
  ai.loaded = false;
}
