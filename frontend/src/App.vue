<script setup>
import { onMounted, onUnmounted, ref } from "vue";
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
function switchTab(key) {
  if (tab.value === key) return;
  tab.value = key;
  if (key === "stats") statsKey.value++;
  window.scrollTo({ top: 0, behavior: "smooth" });
}

// hash ↔ 视图双向同步（#login 为登录视图，兼容旧 /login.html 跳转；#record/id 为详情页）
function onHash() {
  state.view = location.hash === "#login" ? "login" : "home";
  syncDetailFromHash();
}
window.addEventListener("hashchange", onHash);

onMounted(() => {
  init();
});
onUnmounted(() => window.removeEventListener("hashchange", onHash));
</script>

<template>
  <LoginView v-if="state.view === 'login'" />
  <div v-else class="shell">
    <AppHeader />
    <DetailView v-if="state.detailId" />
    <div v-else class="workspace" :data-tab="tab">
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
          <span class="mt-ico">📝</span><span>动态</span>
        </button>
        <button type="button" :class="{ on: tab === 'daily' }" @click="switchTab('daily')">
          <span class="mt-ico">📅</span><span>日报</span>
        </button>
        <button type="button" :class="{ on: tab === 'stats' }" @click="switchTab('stats')">
          <span class="mt-ico">📊</span><span>统计</span>
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
