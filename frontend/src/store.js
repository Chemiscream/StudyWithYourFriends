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
  // 动效偏好（站内「减少动态效果」开关，持久化到 localStorage）
  reduceMotion: false,
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
  // 用户列表（留言 @提及补全用，登录后拉取）
  users: [],
  // 通用提示/确认弹窗（替代原生 alert/confirm，与项目风格一致）
  dialog: { open: false, text: "", confirmMode: false, resolve: null },
  // 编辑态
  editingId: null,
  editHtml: "",
  quoteOf: null, // 待转发的原记录摘要 {id, username, preview}
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
  // 个人档案页（五维人格指数 + 个人打卡）
  persona: {
    open: false, username: "", loading: false, data: null, msg: "", fromDetailId: null,
    // 档案页留言板：items 按 id 倒序（最新在前），hasMore 控制「加载更多」
    board: { items: [], total: 0, hasMore: false, loading: false, posting: false, msg: "" },
    jumpBoard: false, // 从「留言板」通知进来时，数据渲染后滚动到留言板
  },
  // POINT UP 加分动画：实时行为与登录后合并的未读加分公司一份动画
  pointFx: { show: false, gains: [], total: 0, pending: false },
  // RANK UP 升级动画：一次可包含多个维度
  rankFx: { show: false, items: [], pending: false },
  // 播放 POINT UP / RANK UP 动画用的五维实时数据（圆圈大小、五角星形状都靠它）
  fxStats: [],
  lightbox: null, // 图片放大预览 {src}
  settingsOpen: false, // ⚙️ 设置面板（修改个人信息 / 查看更新日志 / AI 设置入口）
  aiSettingsOpen: false, // AI 设置弹窗（服务器AI / 外部 API 切换）
  changelogOpen: false, // 历史更新日志弹窗
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
  if (!state.token) { showDialog("请先登录再点赞"); return; }
  try {
    const res = await request(`/api/records/${rid}/like`, { method: "POST" });
    if (!res.ok) { showDialog(res.message || "操作失败"); return; }
    const rec = state.records.find((r) => r.id === rid);
    if (rec) { rec.like_count = res.like_count; rec.liked = res.liked; }
    if (state.detailRecord && state.detailRecord.id === rid) {
      state.detailRecord.like_count = res.like_count;
      state.detailRecord.liked = res.liked;
    }
    queuePointGains(res.gains); // 点赞自己获得体贴加点时即时 POINT UP
    queueRankUps(res.rank_ups);
  } catch (e) { /* 网络错误不打断 */ }
}

// ---------- 收藏 ----------
export async function toggleFavorite(rid) {
  if (!state.token) { showDialog("请先登录再收藏"); return; }
  try {
    const res = await request(`/api/records/${rid}/favorite`, { method: "POST" });
    if (!res.ok) { showDialog(res.message || "操作失败"); return; }
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
  if (!state.token) { showDialog("请先登录再点赞"); return; }
  try {
    const res = await request(`/api/comments/${cid}/like`, { method: "POST" });
    if (!res.ok) { showDialog(res.message || "操作失败"); return; }
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
  // 留言板通知没有 record_id：打开自己的档案页并定位到留言板
  if (n.type === "board") {
    await openPersona(state.user, { jumpBoard: true });
    return;
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
  // 词云、粒子等 Canvas 需要感知主题切换后重新着色
  window.dispatchEvent(new CustomEvent("studylog:theme", { detail: t }));
}
applyTheme(localStorage.getItem("studylog_theme") || "light");
export function toggleTheme() {
  applyTheme((localStorage.getItem("studylog_theme") || "light") === "dark" ? "light" : "dark");
}

// ---------- 动效偏好 ----------
// 系统级 prefers-reduced-motion 由 CSS 媒体查询兜底；这里再提供站内开关，便于随时关闭动画
export function applyReduceMotion(on) {
  state.reduceMotion = on;
  if (on) document.documentElement.classList.add("reduce-motion");
  else document.documentElement.classList.remove("reduce-motion");
  localStorage.setItem("studylog_reduce_motion", on ? "1" : "0");
}
applyReduceMotion(localStorage.getItem("studylog_reduce_motion") === "1");
export function toggleReduceMotion() {
  applyReduceMotion(!state.reduceMotion);
}

// ---------- 视图 ----------
export function showLogin() { state.view = "login"; location.hash = "#login"; }
export function showHome() { state.view = "home"; location.hash = ""; }

// ---------- 登录态 ----------
function clearAuth() {
  state.token = ""; state.user = ""; state.myColor = ""; state.isAdmin = false; state.activeReply = null;
  state.pointFx.show = false; state.pointFx.gains = []; state.pointFx.pending = false;
  state.rankFx.show = false; state.rankFx.items = []; state.rankFx.pending = false;
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
  state.users = [];
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
    loadPendingPoints(); // 登录后播放别人留下的未读加分
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
    state.users = []; // 用户名已变，清空缓存让下次补全时重新拉取
    await Promise.all([loadRecords(), loadStats(), ensureUsers()]);
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
  state.quoteOf = null;
  state.addMsg = { text: "", cls: "" };
}

/** 转发：打开新增编辑器并带上被引用记录摘要（QQ 空间式转发）。 */
export function startQuote(rec) {
  if (!state.token) return showDialog("请先登录再转发");
  state.editingId = null;
  state.editHtml = "";
  const box = document.createElement("div");
  box.innerHTML = rec.content || "";
  let text = (box.textContent || "").replace(/\s+/g, " ").trim();
  if (text.length > 120) text = text.slice(0, 120) + "…";
  state.quoteOf = { id: rec.id, username: rec.username, preview: text };
  state.addMsg = { text: "", cls: "" };
  window.scrollTo({ top: 0, behavior: "smooth" }); // 编辑器在页面顶部，滚上去输入
}

export function cancelQuote() {
  state.quoteOf = null;
}

/** 点击富文本里的图片 → 全屏放大预览。 */
export function openImage(src) {
  if (src) state.lightbox = { src };
}
export function closeImage() {
  state.lightbox = null;
}

export function cancelEdit() {
  state.editingId = null;
  state.editHtml = "";
  state.quoteOf = null;
  state.addMsg = { text: "", cls: "" };
}

export async function submitRecord(html) {
  const editing = !!state.editingId;
  state.addMsg = { text: editing ? "保存中…" : "添加中…", cls: "" };
  try {
    const payload = { content: html };
    if (!editing && state.quoteOf) payload.quote_id = String(state.quoteOf.id);
    const res = await request(editing ? "/api/records/" + state.editingId : "/api/records", {
      method: editing ? "PUT" : "POST",
      body: JSON.stringify(payload),
    });
    if (res.ok) {
      const quoted = !!state.quoteOf;
      cancelEdit();
      state.addMsg = { text: quoted ? "转发成功" : editing ? "已更新" : "已添加", cls: "ok" };
      queuePointGains(res.gains); // 新记录的五维加分即时 POINT UP（编辑无加分）
      queueRankUps(res.rank_ups);
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
  if (!(await askConfirm("确定删除这条记录？"))) return;
  const res = await request("/api/records/" + id, { method: "DELETE" });
  if (res.ok) {
    if (state.detailId === id) closeDetail(); // 详情页正开着被删的记录时退回主页
    loadRecords();
  }
  else showDialog(res.message || "删除失败");
}

// ---------- 评论 ----------
export async function sendComment(rid, content) {
  if (!content) return;
  const payload = { content };
  if (state.activeReply && state.activeReply.rid === rid) payload.reply_to = state.activeReply.name;
  const res = await request(`/api/records/${rid}/comments`, { method: "POST", body: JSON.stringify(payload) });
  if (res.ok) {
    queuePointGains(res.gains); // 留言带来的体贴加点即时 POINT UP
    queueRankUps(res.rank_ups);
    state.activeReply = null;
    loadRecords();
    if (state.detailId === rid) loadDetail();
  } else {
    showDialog(res.message || "留言失败");
  }
}

export function startReply(rid, name) {
  state.activeReply = { rid, name };
}

export function cancelReply() {
  state.activeReply = null;
}

export async function delComment(cid) {
  if (!(await askConfirm("确定删除这条留言？"))) return;
  const res = await request(`/api/comments/${cid}`, { method: "DELETE" });
  if (res.ok) {
    loadRecords();
    if (state.detailId) loadDetail();
  }
  else showDialog(res.message || "删除失败");
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

// ---------- 个人档案 ----------
// AI 助手是虚拟用户，公开档案接口不返回，点它的昵称不做跳转
const AI_USERNAME = "AI助手";

export async function openPersona(username, opts = {}) {
  const name = (username || state.user || "").trim();
  if (!name || name === AI_USERNAME) return;
  // 从详情页的昵称进入时记住来路，「返回」可以回到那条记录
  state.persona.fromDetailId = state.detailId || null;
  if (state.detailId) {
    state.detailReturnScroll = null;
    closeDetail();
  }
  state.persona.open = true;
  state.persona.username = name;
  state.persona.data = null;
  state.persona.msg = "";
  resetBoard();
  state.persona.jumpBoard = !!opts.jumpBoard; // 从留言板通知进来时，渲染后定位到留言板
  window.scrollTo(0, 0);
  await Promise.all([loadPersona(name), loadBoard(name)]);
}

/** 档案页「返回」：来自详情页就回到那条记录，否则回首页顶部。 */
export function backFromPersona() {
  const rid = state.persona.fromDetailId;
  state.persona.fromDetailId = null;
  closePersona();
  if (rid) openDetail(rid);
  else window.scrollTo(0, 0);
}

export function closePersona() {
  state.persona.open = false;
  state.persona.data = null;
  state.persona.msg = "";
  resetBoard();
}

// ---------- 个人档案留言板 ----------
export function resetBoard() {
  state.persona.board = { items: [], total: 0, hasMore: false, loading: false, posting: false, msg: "" };
  state.persona.jumpBoard = false;
}

/** 拉取留言板：more=true 时按最后一条 id 继续往前翻（游标分页，新留言插队不会导致重复）。 */
export async function loadBoard(username, more = false) {
  const name = username || state.persona.username;
  const b = state.persona.board;
  if (!name || b.loading) return;
  b.loading = true;
  if (!more) b.msg = "";
  try {
    const q = new URLSearchParams({ size: "8" });
    if (more && b.items.length) q.set("before", String(b.items[b.items.length - 1].id));
    const res = await request(`/api/persona/${encodeURIComponent(name)}/board?` + q.toString());
    // 请求返回前已切到别人的档案时丢弃旧响应
    if (state.persona.username !== name) return;
    if (!res.ok) { b.msg = res.message || "留言板加载失败"; return; }
    const list = res.items || [];
    b.items = more ? [...b.items, ...list] : list;
    b.total = res.total || 0;
    b.hasMore = !!res.has_more;
  } catch (e) {
    b.msg = "网络错误";
  } finally {
    b.loading = false;
  }
}

export async function sendBoardMessage(content) {
  const name = state.persona.username;
  const text = (content || "").trim();
  if (!name || !text) return { ok: false };
  if (!state.token) { showDialog("请先登录再留言"); return { ok: false }; }
  const b = state.persona.board;
  b.posting = true;
  try {
    const res = await request(`/api/persona/${encodeURIComponent(name)}/board`, {
      method: "POST",
      body: JSON.stringify({ content: text }),
    });
    if (!res.ok) { showDialog(res.message || "留言失败"); return { ok: false }; }
    queuePointGains(res.gains); // 留言者体贴加分即时 POINT UP
    queueRankUps(res.rank_ups);
    await loadBoard(name);
    return { ok: true };
  } catch (e) {
    showDialog("网络错误");
    return { ok: false };
  } finally {
    b.posting = false;
  }
}

export async function delBoardMessage(id) {
  if (!(await askConfirm("确定删除这条留言？"))) return;
  const res = await request(`/api/persona/board/${id}`, { method: "DELETE" });
  if (res.ok) loadBoard(state.persona.username);
  else showDialog(res.message || "删除失败");
}

export async function loadPersona(username) {
  const name = username || state.persona.username || state.user;
  if (!name) return;
  state.persona.loading = true;
  state.persona.msg = "";
  try {
    // 自己的档案走 /me（公开接口对管理员返回 404，管理员需要看自己的档案）
    const url = name === state.user ? "/api/persona/me" : "/api/persona/" + encodeURIComponent(name);
    const res = await request(url);
    if (!res.ok) {
      state.persona.data = null;
      state.persona.msg = res.message || "加载失败";
      return;
    }
    // 请求返回前已切到别人/关闭页面时丢弃旧响应
    if (state.persona.username === name && state.persona.open) state.persona.data = res;
  } catch (e) {
    state.persona.msg = "网络错误";
  } finally {
    state.persona.loading = false;
  }
}

// ---------- POINT UP 加分动画 ----------
const POINT_NAMES = { knowledge: "知识", courage: "勇气", dexterity: "灵巧", kindness: "体贴", charm: "魅力" };
let pointFxTimer = null;

/**
 * 播放加分动画。gains 可以是 {stat: delta}（接口返回）或 [{key,name,delta}]（未读加分接口）。
 * 短时间内的多次加分合并进同一个动画，不重复弹出。
 */
export function queuePointGains(gains, pending = false) {
  if (!gains) return;
  // 同时有 RANK UP 时只播 RANK UP，避免两个动画叠在一起
  if (state.rankFx.show) return;
  const list = Array.isArray(gains)
    ? gains
    : Object.entries(gains).map(([key, delta]) => ({ key, name: POINT_NAMES[key] || key, delta }));
  const valid = list.filter((g) => g && g.delta > 0);
  if (!valid.length) return;

  // 上一轮动画已结束（show=false）时从头算起，避免旧加分混进新的 POINT UP
  const base = state.pointFx.show ? state.pointFx.gains : [];
  const merged = new Map(base.map((g) => [g.key, { ...g }]));
  for (const g of valid) {
    const cur = merged.get(g.key);
    if (cur) cur.delta += g.delta;
    else merged.set(g.key, { key: g.key, name: g.name || POINT_NAMES[g.key] || g.key, delta: g.delta });
  }
  state.pointFx.gains = [...merged.values()];
  state.pointFx.total = state.pointFx.gains.reduce((n, g) => n + g.delta, 0);
  state.pointFx.pending = pending || state.pointFx.pending;
  state.pointFx.show = true;
  loadFxStats();

  clearTimeout(pointFxTimer);
  pointFxTimer = setTimeout(hidePointFx, state.pointFx.pending ? 3600 : 3000);
}

function hidePointFx() {
  state.pointFx.show = false;
  // 未读加分播放完立即上报已读，避免下次打开站点重复弹
  if (state.pointFx.pending) {
    state.pointFx.pending = false;
    request("/api/persona/pending/seen", { method: "POST" }).catch(() => {});
  }
  clearTimeout(pointFxTimer);
  pointFxTimer = setTimeout(() => {
    if (!state.pointFx.show) state.pointFx.gains = [];
  }, 400);
}

/** 拉取五维实时数据供 POINT UP / RANK UP 动画绘制圆圈大小与五角星形状。 */
export async function loadFxStats() {
  if (!state.token) return;
  try {
    const res = await request("/api/persona/me");
    if (res && res.ok && Array.isArray(res.stats)) state.fxStats = res.stats;
  } catch (e) { /* 拉不到就用上一次的数据，动画不阻塞 */ }
}

/** 登录后拉取「别人带来的」未读加分并合并播放一次；没有未读时不弹任何东西。 */
export async function loadPendingPoints() {
  if (!state.token) return;
  try {
    const res = await request("/api/persona/pending");
    if (!res.ok || !res.total) return;
    queuePointGains(res.gains, true);
    queueRankUps(res.rank_ups, true);
  } catch (e) { /* 网络错误不打扰 */ }
}

// ---------- RANK UP 升级动画 ----------
let rankFxTimer = null;

/** 播放升级动画。同一维度多次升级合并，只保留最高等级；多个维度放进同一张卡片。 */
export function queueRankUps(list, pending = false) {
  if (!Array.isArray(list) || !list.length) return;
  const merged = new Map((state.rankFx.show ? state.rankFx.items : []).map((i) => [i.key, { ...i }]));
  for (const r of list) {
    if (!r || !r.rank) continue;
    const cur = merged.get(r.key);
    if (!cur || r.rank > cur.rank) merged.set(r.key, { ...r });
  }
  if (!merged.size) return;
  // POINT UP 与 RANK UP 同时出现时只播 RANK UP
  if (state.pointFx.show) hidePointFx();
  state.rankFx.items = [...merged.values()];
  state.rankFx.pending = pending || state.rankFx.pending;
  state.rankFx.show = true;
  loadFxStats();

  clearTimeout(rankFxTimer);
  rankFxTimer = setTimeout(hideRankFx, 4600);
}

function hideRankFx() {
  state.rankFx.show = false;
  state.rankFx.pending = false;
  clearTimeout(rankFxTimer);
  rankFxTimer = setTimeout(() => {
    if (!state.rankFx.show) state.rankFx.items = [];
  }, 500);
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

// ---------- 通用提示/确认弹窗 ----------
// 纯提示（替代 alert）
export function showDialog(text) {
  state.dialog.open = true;
  state.dialog.text = text;
  state.dialog.confirmMode = false;
  state.dialog.resolve = null;
}
// 确认（替代 confirm）：确定 resolve(true)，取消/遮罩关闭 resolve(false)
export function askConfirm(text) {
  return new Promise((resolve) => {
    state.dialog.open = true;
    state.dialog.text = text;
    state.dialog.confirmMode = true;
    state.dialog.resolve = resolve;
  });
}
export function closeDialog(val) {
  if (state.dialog.confirmMode && state.dialog.resolve) state.dialog.resolve(val);
  state.dialog.open = false;
  state.dialog.resolve = null;
}

// ---------- 用户列表（@提及补全） ----------
export async function ensureUsers() {
  if (state.users.length || !state.token) return;
  try {
    const res = await request("/api/users");
    if (res.ok) state.users = res.users || [];
  } catch (e) { /* 补全列表拉取失败不打断输入 */ }
}

// ---------- 启动 ----------
export async function init() {
  checkVersion();
  await loadLatestDaily(); // 日报公开：未登录也加载
  if (state.token) {
    await refreshMe();
    ensureUsers();
    loadNotifications();
    startNotifPoll();
    loadPendingPoints(); // 别人带来的未读五维加分会合并成一次 POINT UP
  }
  await loadRecords();
  syncDetailFromHash(); // 直接打开 #record/id 链接（刷新/分享）时加载详情页
}

// AI 模块在 useAiChat.js 中定义；logout 需要重置 AI 状态，延迟绑定避免循环依赖
import { resetAi } from "./useAiChat.js";
function aiReset() { resetAi(); }
