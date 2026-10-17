// 全局状态（单例 reactive，等价于原 index.html 的全局变量 + 函数集合）
import { reactive } from "vue";
import { request, getToken, setToken, authHeaders } from "./api.js";

export const PALETTE = ["4a90d9","e74c3c","27ae60","f39c12","9b59b6","1abc9c","e67e22","34495e","d35400","8e44ad","16a085","c0392b","2980b9","f1c40f","7f8c8d"];
export const EMOJIS = [
  "😀","😄","😂","🤣","😊","😍","🥰","😎","🤓","🧐","🤔","😅","😭","😤","🥳","😴",
  "👍","👎","👏","🙏","💪","🤝","✌️","🫶","❤️","🔥","✨","⭐","🎉","🎊","💯","🌟",
  "📚","📖","✏️","📝","💡","🧠","🔍","📌","📅","⏰","⌛","🚀","🎯","🏆","☕","🍵",
];

export const state = reactive({
  // 登录态
  token: getToken(),
  user: localStorage.getItem("studylog_user") || "",
  isAdmin: localStorage.getItem("studylog_admin") === "1",
  myColor: "",
  // 视图：home / login（hash 同步，兼容旧 /login.html → /#login）
  view: location.hash === "#login" ? "login" : "home",
  // 记录
  records: [],
  filter: { q: "", from: "", to: "" },
  activeReply: null, // {rid, name} 普通留言时为 null
  // 编辑态
  editingId: null,
  editHtml: "",
  addMsg: { text: "", cls: "" },
  // 统计
  statsTotal: 0,
  ranking: [],
  words: [],
  // 版本
  version: "",
  update: null, // {version, notes}
  // emoji 面板（全站单例）
  emoji: { visible: false, kind: null, inputEl: null, savedRange: null, anchorEl: null },
  profileOpen: false,
  // AI 日报
  daily: {
    listOpen: false,
    detailOpen: false,
    detail: null,      // { report_date, summary, stat }
    list: [],          // [{ report_date, summary_preview, has_summary, total }]
    page: 0,
    size: 5,
    hasMore: true,
    loading: false,
    msg: "",
  },
});

// ---------- 工具 ----------
export function fmtTime(iso) {
  return new Date(iso).toLocaleString("zh-CN", { hour12: false });
}
export function fmtComment(iso) {
  return new Date(iso).toLocaleString("zh-CN", { hour12: false, month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

// ---------- 主题 ----------
export function applyTheme(t) {
  if (t === "dark") document.documentElement.setAttribute("data-theme", "dark");
  else document.documentElement.removeAttribute("data-theme");
  localStorage.setItem("studylog_theme", t);
}
applyTheme(localStorage.getItem("studylog_theme") || "light");
export function toggleTheme() {
  applyTheme((localStorage.getItem("studylog_theme") || "light") === "dark" ? "light" : "dark");
}

// ---------- 视图 ----------
export function showLogin() { state.view = "login"; location.hash = "#login"; }
export function showHome() { state.view = "home"; location.hash = ""; }

// ---------- 登录态 ----------
function clearAuth() {
  state.token = ""; state.user = ""; state.myColor = ""; state.isAdmin = false; state.activeReply = null;
  setToken("");
  localStorage.removeItem("studylog_user");
  localStorage.removeItem("studylog_admin");
}

export async function refreshMe() {
  try {
    const res = await request("/api/me");
    if (res.ok && res.username) {
      state.user = res.username;
      state.myColor = res.color || "4a90d9";
      state.isAdmin = !!res.is_admin;
      localStorage.setItem("studylog_admin", state.isAdmin ? "1" : "0");
    } else {
      clearAuth();
    }
  } catch (e) { /* 服务不可达时保持本地登录态 */ }
}

export function logout() {
  clearAuth();
  cancelEdit(); // 清理编辑态，避免残留到下一次登录
  aiReset(); // 引用在文件底部定义前被调用？不会：logout 由用户点击触发，届时模块已初始化
}

export async function doAuth(mode, username, password) {
  if (!username || !password) return { ok: false, message: "请输入用户名和密码" };
  try {
    const res = await request(mode === "login" ? "/api/login" : "/api/register", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
    if (!res.ok) return { ok: false, message: res.message || "操作失败" };
    setToken(res.token);
    localStorage.setItem("studylog_user", res.username);
    localStorage.setItem("studylog_admin", res.is_admin ? "1" : "0");
    state.token = res.token;
    await refreshMe();
    showHome();
    return { ok: true };
  } catch (e) {
    return { ok: false, message: "网络错误" };
  }
}

export async function chooseColor(c) {
  const res = await request("/api/users/color", { method: "PUT", body: JSON.stringify({ color: c }) });
  if (res.ok) {
    state.myColor = res.color;
    await loadRecords();
    return { ok: true };
  }
  return { ok: false, message: res.message || "设置失败" };
}

export async function renameUser(newName) {
  const res = await request("/api/users/rename", { method: "PUT", body: JSON.stringify({ new_username: newName }) });
  if (res.ok) {
    state.user = res.username;
    state.token = res.token;
    setToken(res.token);
    localStorage.setItem("studylog_user", state.user);
    await Promise.all([loadRecords(), loadStats()]);
    return { ok: true };
  }
  return { ok: false, message: res.message || "修改失败" };
}

export async function changePassword(oldPwd, newPwd) {
  const res = await request("/api/users/password", {
    method: "PUT",
    body: JSON.stringify({ old_password: oldPwd, new_password: newPwd }),
  });
  return res.ok ? { ok: true } : { ok: false, message: res.message || "修改失败" };
}

// ---------- 记录 ----------
export async function loadRecords() {
  const params = new URLSearchParams();
  if (state.filter.q) params.set("q", state.filter.q);
  if (state.filter.from) params.set("from", state.filter.from);
  if (state.filter.to) params.set("to", state.filter.to);
  const res = await request("/api/records?" + params.toString());
  state.records = res.records || [];
  loadStats();
}

export async function search(f) {
  state.filter = { q: f.q.trim(), from: f.from, to: f.to };
  await loadRecords();
}

export function startEdit(rec) {
  state.editingId = rec.id;
  state.editHtml = rec.content || "";
  state.addMsg = { text: "", cls: "" };
}

export function cancelEdit() {
  state.editingId = null;
  state.editHtml = "";
  state.addMsg = { text: "", cls: "" };
}

export async function submitRecord(html) {
  const editing = !!state.editingId;
  state.addMsg = { text: editing ? "保存中…" : "添加中…", cls: "" };
  try {
    const res = await request(editing ? "/api/records/" + state.editingId : "/api/records", {
      method: editing ? "PUT" : "POST",
      body: JSON.stringify({ content: html }),
    });
    if (res.ok) {
      cancelEdit();
      state.addMsg = { text: editing ? "已更新" : "已添加", cls: "ok" };
      await loadRecords();
      return { ok: true };
    }
    state.addMsg = { text: res.message || "操作失败", cls: "err" };
    return { ok: false };
  } catch (e) {
    state.addMsg = { text: "网络错误", cls: "err" };
    return { ok: false };
  }
}

export async function delRecord(id) {
  if (!confirm("确定删除这条记录？")) return;
  const res = await request("/api/records/" + id, { method: "DELETE" });
  if (res.ok) loadRecords();
  else alert(res.message || "删除失败");
}

// ---------- 评论 ----------
export async function sendComment(rid, content) {
  if (!content) return;
  const payload = { content };
  if (state.activeReply && state.activeReply.rid === rid) payload.reply_to = state.activeReply.name;
  const res = await request(`/api/records/${rid}/comments`, { method: "POST", body: JSON.stringify(payload) });
  if (res.ok) {
    state.activeReply = null;
    loadRecords();
  } else {
    alert(res.message || "留言失败");
  }
}

export function startReply(rid, name) {
  state.activeReply = { rid, name };
}

export function cancelReply() {
  state.activeReply = null;
}

export async function delComment(cid) {
  if (!confirm("确定删除这条留言？")) return;
  const res = await request(`/api/comments/${cid}`, { method: "DELETE" });
  if (res.ok) loadRecords();
  else alert(res.message || "删除失败");
}

// ---------- 统计 ----------
export async function loadStats() {
  try {
    const res = await request("/api/stats");
    if (!res.ok) return;
    state.statsTotal = res.total || 0;
    state.ranking = res.ranking || [];
    state.words = res.words || [];
  } catch (e) { /* 统计失败不影响主功能 */ }
}

// ---------- 版本 ----------
export async function checkVersion() {
  try {
    const res = await request("/api/version");
    if (!res.ok || !res.version) return;
    state.version = res.version;
    if (localStorage.getItem("studylog_seen_version") !== res.version) {
      state.update = { version: res.version, notes: res.notes || [] };
    }
  } catch (e) { /* 版本检查失败不影响主功能 */ }
}

export function dismissUpdate() {
  if (state.update) localStorage.setItem("studylog_seen_version", state.update.version);
  state.update = null;
}

// ---------- emoji 面板 ----------
export function toggleEmoji(kind, inputEl, savedRange, anchorEl) {
  const em = state.emoji;
  if (em.visible && em.kind === kind && em.inputEl === inputEl) {
    closeEmoji();
    return;
  }
  em.kind = kind;
  em.inputEl = inputEl || null;
  em.savedRange = savedRange || null;
  em.anchorEl = anchorEl || null;
  em.visible = true;
}
export function closeEmoji() {
  const em = state.emoji;
  em.visible = false; em.kind = null; em.inputEl = null; em.savedRange = null; em.anchorEl = null;
}

// ---------- AI 日报 ----------
export async function openDailyList() {
  state.daily.listOpen = true;
  state.daily.page = 0;
  state.daily.list = [];
  state.daily.hasMore = true;
  await loadDailyList();
}
export function closeDailyList() { state.daily.listOpen = false; }

export async function loadDailyList() {
  state.daily.loading = true;
  state.daily.msg = "";
  try {
    const res = await request(`/api/daily-reports?page=${state.daily.page}&size=${state.daily.size}`);
    if (!res.ok) { state.daily.msg = res.message || "加载失败"; return; }
    state.daily.list = res.items || [];
    state.daily.hasMore = !!res.has_more;
  } catch (e) {
    state.daily.msg = "网络错误";
  } finally {
    state.daily.loading = false;
  }
}

export function dailyPrevPage() {
  if (state.daily.page > 0) { state.daily.page--; loadDailyList(); }
}
export function dailyNextPage() {
  if (state.daily.hasMore) { state.daily.page++; loadDailyList(); }
}

export async function openDailyDetail(date) {
  state.daily.msg = "";
  try {
    const res = await request("/api/daily-reports/" + date);
    if (!res.ok) { state.daily.msg = res.message || "加载失败"; return; }
    state.daily.detail = { report_date: res.report_date, summary: res.summary, stat: res.stat || {} };
    state.daily.detailOpen = true;
  } catch (e) {
    state.daily.msg = "网络错误";
  }
}
export function closeDailyDetail() { state.daily.detailOpen = false; state.daily.detail = null; }

export async function generateDailyReport(date) {
  state.daily.msg = "生成中…";
  try {
    const res = await request("/api/daily-reports/generate", {
      method: "POST",
      body: JSON.stringify({ date }),
    });
    if (!res.ok) { state.daily.msg = res.message || "生成失败"; return; }
    state.daily.detail = { report_date: res.report_date, summary: res.summary, stat: res.stat || {} };
    state.daily.detailOpen = true;
    state.daily.msg = res.message || "已生成";
    // 刷新列表
    if (state.daily.listOpen) loadDailyList();
  } catch (e) {
    state.daily.msg = "网络错误";
  }
}

// ---------- 启动 ----------
export async function init() {
  checkVersion();
  if (state.token) {
    await refreshMe();
  }
  await loadRecords();
}

// AI 模块在 useAiChat.js 中定义；logout 需要重置 AI 状态，延迟绑定避免循环依赖
import { resetAi } from "./useAiChat.js";
function aiReset() { resetAi(); }
