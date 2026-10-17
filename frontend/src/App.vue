<script setup>
import { onMounted, onUnmounted } from "vue";
import { state, init } from "./store.js";
import AppHeader from "./components/AppHeader.vue";
import LoginView from "./components/LoginView.vue";
import StatsPanel from "./components/StatsPanel.vue";
import AiChatPanel from "./components/AiChatPanel.vue";
import DailyReportPanel from "./components/DailyReportPanel.vue";
import DailyReportListModal from "./components/DailyReportListModal.vue";
import DailyReportDetailModal from "./components/DailyReportDetailModal.vue";
import RecordEditor from "./components/RecordEditor.vue";
import FilterBar from "./components/FilterBar.vue";
import RecordList from "./components/RecordList.vue";
import ProfileModal from "./components/ProfileModal.vue";
import UpdateModal from "./components/UpdateModal.vue";
import EmojiPanel from "./components/EmojiPanel.vue";

// hash ↔ 视图双向同步（#login 为登录视图，兼容旧 /login.html 跳转）
function onHash() {
  state.view = location.hash === "#login" ? "login" : "home";
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
    <StatsPanel />
    <AiChatPanel v-if="state.token" />
    <DailyReportPanel v-if="state.token" />
    <RecordEditor v-if="state.token" />
    <FilterBar />
    <RecordList />
    <span v-if="state.version" class="ver-tag">v{{ state.version }}</span>
  </div>
  <EmojiPanel />
  <ProfileModal v-if="state.profileOpen" />
  <UpdateModal v-if="state.update" />
  <DailyReportListModal v-if="state.daily.listOpen" />
  <DailyReportDetailModal v-if="state.daily.detailOpen" />
</template>
