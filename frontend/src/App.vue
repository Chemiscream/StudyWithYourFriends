<script setup>
import { onMounted, onUnmounted, ref, computed } from "vue";
import { state, init, syncDetailFromHash } from "./store.js";
import AppHeader from "./components/AppHeader.vue";
import LoginView from "./components/LoginView.vue";
import StatsPanel from "./components/StatsPanel.vue";
import MascotWidget from "./components/MascotWidget.vue";
import DailyReportPanel from "./components/DailyReportPanel.vue";
import StudyHeatmap from "./components/StudyHeatmap.vue";
import DailyReportListModal from "./components/DailyReportListModal.vue";
import DailyReportDetailModal from "./components/DailyReportDetailModal.vue";
import RecordEditor from "./components/RecordEditor.vue";
import FilterBar from "./components/FilterBar.vue";
import RecordList from "./components/RecordList.vue";
import DetailView from "./components/DetailView.vue";
import ProfileModal from "./components/ProfileModal.vue";
import SettingsModal from "./components/SettingsModal.vue";
import AiSettingsModal from "./components/AiSettingsModal.vue";
import ChangelogModal from "./components/ChangelogModal.vue";
import ImageLightbox from "./components/ImageLightbox.vue";
import NotifModal from "./components/NotifModal.vue";
import FavoriteModal from "./components/FavoriteModal.vue";
import UpdateModal from "./components/UpdateModal.vue";
import EmojiPanel from "./components/EmojiPanel.vue";
import AppDialog from "./components/AppDialog.vue";

// 移动端模块分页：feed=动态、daily=日报、stats=统计（桌面端由 CSS 忽略，三个模块同屏并列）
const tab = ref("feed");
// 统计面板首次渲染在隐藏容器里时画布宽度为 0，切到统计页时重挂载以按真实宽度重绘词云
const statsKey = ref(0);
// 移动端切页动画方向：next=向右翻页，prev=向左回翻
const TAB_ORDER = ["feed", "daily", "stats"];
const tabAnim = ref("");
let tabAnimTimer = null;
function switchTab(key) {
  if (tab.value === key) return;
  const dir = TAB_ORDER.indexOf(key) > TAB_ORDER.indexOf(tab.value) ? "next" : "prev";
  tab.value = key;
  if (key === "stats") statsKey.value++;
  // 先清空再在下一帧挂上方向，保证连续两次同方向切换也能重新播放
  tabAnim.value = "";
  requestAnimationFrame(() => { tabAnim.value = dir; });
  clearTimeout(tabAnimTimer);
  tabAnimTimer = setTimeout(() => { tabAnim.value = ""; }, 640);
  window.scrollTo({ top: 0, behavior: "smooth" });
}

// hash ↔ 视图双向同步（#login 为登录视图，兼容旧 /login.html 跳转；#record/id 为详情页）
function onHash() {
  state.view = location.hash === "#login" ? "login" : "home";
  syncDetailFromHash();
}
window.addEventListener("hashchange", onHash);

// ---------- Hero 实时数据（不新增请求，全部来自已加载记录） ----------
const now = ref(new Date());
let clockTimer = null;

function shKey(d) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit",
  }).format(d);
}
const todayKey = computed(() => shKey(now.value));
const dateLabel = computed(() => {
  const d = now.value;
  const wd = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"][d.getDay()];
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}.${p(d.getMonth() + 1)}.${p(d.getDate())} / ${wd}`;
});
const clock = computed(() => {
  const p = (n) => String(n).padStart(2, "0");
  return `${p(now.value.getHours())}:${p(now.value.getMinutes())}:${p(now.value.getSeconds())}`;
});
const dayMap = computed(() => {
  const m = {};
  for (const r of state.records || []) {
    if (!r.created_at) continue;
    const d = new Date(r.created_at);
    if (Number.isNaN(d.getTime())) continue;
    const k = shKey(d);
    m[k] = (m[k] || 0) + 1;
  }
  return m;
});
const todayCount = computed(() => dayMap.value[todayKey.value] || 0);
const participants = computed(() => new Set((state.records || []).map((r) => r.username)).size);
const DAY_MS = 86400000;
const streak = computed(() => {
  const map = dayMap.value;
  const keyOf = (d) => d.toISOString().slice(0, 10);
  let d = new Date(todayKey.value + "T00:00:00Z");
  if (!(map[todayKey.value] > 0)) d = new Date(d.getTime() - DAY_MS);
  let n = 0;
  while (n < 400 && map[keyOf(d)] > 0) {
    n++;
    d = new Date(d.getTime() - DAY_MS);
  }
  return n;
});

onMounted(() => {
  init();
  clockTimer = setInterval(() => { now.value = new Date(); }, 1000);
});
onUnmounted(() => {
  window.removeEventListener("hashchange", onHash);
  clearInterval(clockTimer);
  clearTimeout(tabAnimTimer);
});
</script>

<template>
  <LoginView v-if="state.view === 'login'" />
  <div v-else class="shell">
    <div class="p3-bg" aria-hidden="true">
      <div class="bg-grad"></div>
      <div class="bg-moon"></div>
      <div class="bg-rings"><i></i><i></i><i></i></div>
      <div class="bg-grid"></div>
      <div class="bg-scan"></div>
      <div class="bg-vignette"></div>
    </div>
    <AppHeader />
    <section v-if="!state.detailId" class="hero" :class="{ 'hero-tab-hide': tab !== 'feed' }">
      <div class="hero-lines" aria-hidden="true"></div>
      <div class="hero-main">
        <div class="hero-eyebrow"><i class="dot"></i>{{ dateLabel }}<span class="sl">/</span>AFTER SCHOOL</div>
        <h1 class="hero-title">
          <span class="line"><span>STUDY</span></span>
          <span class="line"><span class="outline">WITH YOUR</span></span>
          <span class="line"><span>FRIENDS</span><em class="cursor">_</em></span>
        </h1>
        <p class="hero-sub">把今天的努力，刻进明天的档案。</p>
        <div class="hero-stats">
          <div class="hs"><b>{{ todayCount }}</b><span>RECORDS<br>今日记录</span></div>
          <div class="hs"><b>{{ participants }}</b><span>MEMBERS<br>参与同学</span></div>
          <div class="hs"><b>{{ streak }}</b><span>DAY STREAK<br>连续打卡</span></div>
        </div>
      </div>
      <div class="hero-moon" aria-hidden="true">
        <div class="moon-halo"></div>
        <div class="moon-ring r1"></div>
        <div class="moon-ring r2"></div>
        <div class="moon-core"><span>{{ clock }}</span><small>LOCAL TIME</small></div>
        <div class="moon-tag">AFTER<br>SCHOOL</div>
        <i class="moon-spark s1"></i><i class="moon-spark s2"></i><i class="moon-spark s3"></i>
      </div>
      <div class="hero-code">ARCHIVE // 001</div>
    </section>
    <DetailView v-if="state.detailId" />
    <div v-else class="workspace" :data-tab="tab" :data-anim="tabAnim || undefined">
      <main class="col-main">
        <RecordEditor v-if="state.token" />
        <FilterBar />
        <RecordList />
      </main>
      <aside class="col-side">
        <div class="module-block" data-module="daily">
          <DailyReportPanel :expanded="tab === 'daily'" />
        </div>
        <div class="module-block" data-module="heatmap">
          <StudyHeatmap />
        </div>
        <div class="module-block" data-module="stats">
          <StatsPanel :key="statsKey" />
        </div>
      </aside>
      <nav class="module-tabs" aria-label="模块切换">
        <button type="button" :class="{ on: tab === 'feed' }" @click="switchTab('feed')">
          <span class="mt-ico"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3h9l5 5v13H5z"/><path d="M14 3v5h5"/><path d="M8 13h8M8 17h5"/></svg></span><span>动态</span>
        </button>
        <button type="button" :class="{ on: tab === 'daily' }" @click="switchTab('daily')">
          <span class="mt-ico"><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="5" width="17" height="15"/><path d="M8 3v4M16 3v4M3.5 9.5h17"/><path d="M7.5 13h2M11 13h2M14.5 13h2M7.5 16.5h2M11 16.5h2"/></svg></span><span>日报</span>
        </button>
        <button type="button" :class="{ on: tab === 'stats' }" @click="switchTab('stats')">
          <span class="mt-ico"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20V11M10 20V5M16 20v-6M22 20H2"/></svg></span><span>统计</span>
        </button>
      </nav>
    </div>
    <span v-if="state.version" class="ver-tag">v{{ state.version }}</span>
  </div>
  <EmojiPanel />
  <NotifModal v-if="state.notif.open" />
  <FavoriteModal v-if="state.favorites.open" />
  <ProfileModal v-if="state.profileOpen" />
  <SettingsModal v-if="state.settingsOpen" />
  <AiSettingsModal v-if="state.aiSettingsOpen" />
  <ChangelogModal v-if="state.changelogOpen" />
  <ImageLightbox v-if="state.lightbox" />
  <UpdateModal v-if="state.update" />
  <DailyReportListModal v-if="state.daily.listOpen" />
  <DailyReportDetailModal v-if="state.daily.detailOpen" />
  <AppDialog />
  <MascotWidget v-if="state.view !== 'login'" />
</template>
