// 全局状态（单例 reactive，等价于原 index.html 的全局变量 + 函数集合）
import { reactive, nextTick } from "vue";
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
  // 详情页（hash #record/id 同步，占满页面）
  detailId: parseDetailHash(),
  detailRecord: null,
  detailLoading: false,
  detailMsg: "",
  detailReturnScroll: null, // 从动态列表进入详情时记住的滚动位置；null 表示返回时回顶部（如从消息进入）
  // 通知
  notif: { open: false, unread: 0, items: [], loading: false },
  // 我的收藏（弹窗预览）
  favorites: { open: false, items: [], loading: false, msg: "" },
  // 编辑态
  editingId: null,
  editHtml: "",
  addMsg: { text: "", cls: "" },
  // 统计
  statsTotal: 0,
  statsRange: "all", // all / month / week / today
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
    latest: null,      // { report_date, summary, stat } 首页面板预览
    list: [],          // [{ report_date, summary, summary_preview, has_summary, stat, total }]
    page: 0,
    size: 3,
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

// ---------- 详情页（hash #record/id 同步） ----------
export function parseDetailHash() {
  const m = /^#record\/(\d+)$/.exec(location.hash);
  return m ? Number(m[1]) : null;
}

export function openDetail(id, fromNotif = false) {
  // 从动态列表进入时记住滚动位置，返回时恢复；从消息进入时返回首页顶部
  state.detailReturnScroll = fromNotif ? null : window.scrollY;
  if (state.detailId === id && state.detailRecord) { window.scrollTo(0, 0); return; }
  location.hash = "#record/" + id; // hashchange 里统一加载
}

export function closeDetail() {
  state.detailId = null;
  state.detailRecord = null;
  state.detailMsg = "";
  state.activeReply = null;
  if (location.hash.startsWith("#record/")) {
    // 用 replaceState 去掉 hash，避免回退键落回详情页
    history.replaceState(null, "", location.pathname + location.search);
  }
  // 从动态列表进入的返回原滚动位置，从消息进入的（null）回首页顶部；等主页重新渲染后再滚动
  const s = state.detailReturnScroll;
  state.detailReturnScroll = null;
  nextTick(() => window.scrollTo(0, s == null ? 0 : s));
}

/** hash 变化时同步详情页状态并加载数据（初始加载也调用一次）。 */
export function syncDetailFromHash() {
  const id = parseDetailHash();
  if (id !== state.detailId) {
    state.detailId = id;
    state.detailRecord = null;
    state.detailMsg = "";
    state.activeReply = null;
    if (id) window.scrollTo(0, 0); // 进入详情页从顶部开始看
  }
  if (id) loadDetail();
}

export async function loadDetail() {
  if (!state.detailId) return;
  state.detailLoading = true;
  state.detailMsg = "";
  try {
    const res = await request("/api/records/" + state.detailId);
    if (res.ok) {
      state.detailRecord = res.record;
    } else {
      state.detailRecord = null;
      state.detailMsg = res.message || "记录不存在或已被删除";
    }
  } catch (e) {
    state.detailMsg = "网络错误";
  } finally {
    state.detailLoading = false;
  }
}

// ---------- 点赞 ----------
export async function toggleLike(rid) {
  if (!state.token) { alert("请先登录再点赞"); return; }
  try {
    const res = await request(`/api/records/${rid}/like`, { method: "POST" });
    if (!res.ok) { alert(res.message || "操作失败"); return; }
    const rec = state.records.find((r) => r.id === rid);
    if (rec) { rec.like_count = res.like_count; rec.liked = res.liked; }
    if (state.detailRecord && state.detailRecord.id === rid) {
      state.detailRecord.like_count = res.like_count;
      state.detailRecord.liked = res.liked;
    }
  } catch (e) { /* 网络错误不打断 */ }
}

// ---------- 收藏 ----------
export async function toggleFavorite(rid) {
  if (!state.token) { alert("请先登录再收藏"); return; }
  try {
    const res = await request(`/api/records/${rid}/favorite`, { method: "POST" });
    if (!res.ok) { alert(res.message || "操作失败"); return; }
    const rec = state.records.find((r) => r.id === rid);
    if (rec) rec.favorited = res.favorited;
    if (state.detailRecord && state.detailRecord.id === rid) state.detailRecord.favorited = res.favorited;
    if (state.favorites.open) loadFavorites(); // 弹窗开着时同步列表（取消收藏的条目实时移除）
  } catch (e) { /* 网络错误不打断 */ }
}

export function openFavorites() {
  state.favorites.open = true;
  loadFavorites();
}
export function closeFavorites() {
  state.favorites.open = false;
  state.favorites.msg = "";
}

export async function loadFavorites() {
  if (!state.token) return;
  state.favorites.loading = true;
  state.favorites.msg = "";
  try {
    const res = await request("/api/favorites");
    if (!res.ok) { state.favorites.msg = res.message || "加载失败"; return; }
    state.favorites.items = res.records || [];
  } catch (e) {
    state.favorites.msg = "网络错误";
  } finally {
    state.favorites.loading = false;
  }
}

// ---------- 留言点赞 ----------
export async function toggleCommentLike(cid) {
  if (!state.token) { alert("请先登录再点赞"); return; }
  try {
    const res = await request(`/api/comments/${cid}/like`, { method: "POST" });
    if (!res.ok) { alert(res.message || "操作失败"); return; }
    const apply = (cs) => {
      const c = (cs || []).find((x) => x.id === cid);
      if (c) { c.like_count = res.like_count; c.liked = res.liked; }
    };
    for (const rec of state.records) apply(rec.comments);
    if (state.detailRecord) apply(state.detailRecord.comments);
  } catch (e) { /* 网络错误不打断 */ }
}

// ---------- 通知 ----------
let notifTimer = null;
export async function loadNotifications() {
  if (!state.token) return;
  try {
    const res = await request("/api/notifications");
    if (!res.ok) return;
    state.notif.unread = res.unread || 0;
    state.notif.items = res.items || [];
  } catch (e) { /* 通知失败不影响主功能 */ }
}
export function startNotifPoll() {
  stopNotifPoll();
  notifTimer = setInterval(loadNotifications, 30000);
}
export function stopNotifPoll() {
  if (notifTimer) { clearInterval(notifTimer); notifTimer = null; }
}
/** 点击通知：标记已读并跳转对应记录详情页（返回时回首页顶部）。 */
export async function openNotification(n) {
  state.notif.open = false;
  if (!n.read) {
    request(`/api/notifications/${n.id}/read`, { method: "POST" }).then(loadNotifications);
  }
  openDetail(n.record_id, true);
}
export async function clearNotifications() {
  try {
    const res = await request("/api/notifications/clear", { method: "POST" });
    if (res.ok) {
      state.notif.items = [];
      state.notif.unread = 0;
    }
  } catch (e) { /* 忽略 */ }
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
  stopNotifPoll();
  state.notif = { open: false, unread: 0, items: [], loading: false };
  state.favorites = { open: false, items: [], loading: false, msg: "" };
  closeDetail();
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
    loadNotifications();
    startNotifPoll();
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
  if (res.ok) {
    if (state.detailId === id) closeDetail(); // 详情页正开着被删的记录时退回主页
    loadRecords();
  }
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
    if (state.detailId === rid) loadDetail();
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
  if (res.ok) {
    loadRecords();
    if (state.detailId) loadDetail();
  }
  else alert(res.message || "删除失败");
}

// ---------- 统计 ----------
export async function loadStats(range) {
  if (range) state.statsRange = range;
  try {
    const res = await request(`/api/stats?range=${state.statsRange}`);
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
export async function loadLatestDaily() {
  try {
    const res = await request("/api/daily-reports/latest");
    if (res.ok) state.daily.latest = res.report || null;
  } catch (e) { /* 忽略 */ }
}

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

// ---------- 启动 ----------
export async function init() {
  checkVersion();
  await loadLatestDaily(); // 日报公开：未登录也加载
  if (state.token) {
    await refreshMe();
    loadNotifications();
    startNotifPoll();
  }
  await loadRecords();
  syncDetailFromHash(); // 直接打开 #record/id 链接（刷新/分享）时加载详情页
}

// AI 模块在 useAiChat.js 中定义；logout 需要重置 AI 状态，延迟绑定避免循环依赖
import { resetAi } from "./useAiChat.js";
function aiReset() { resetAi(); }
