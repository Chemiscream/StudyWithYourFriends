<script setup>
import { computed } from "vue";
import { state, closeDailyList, dailyPrevPage, dailyNextPage, openDailyDetail } from "../store.js";

const d = state.daily;
const pageInfo = computed(() => `第 ${d.page + 1} 页`);

function subtitle(item) {
  const s = item.summary;
  if (s && s.trim()) {
    const one = s.replace(/\s+/g, " ").trim();
    return one.length > 20 ? one.slice(0, 20) + "…" : one;
  }
  return `共 ${item.total || 0} 条记录`;
}
</script>

<template>
  <div class="modal-mask" @click.self="closeDailyList">
    <div class="modal-card daily-list-card">
      <h2>📅 AI 日报</h2>

      <div v-if="d.loading" class="daily-loading">加载中…</div>
      <div v-else-if="!d.list.length" class="daily-empty">还没有日报</div>

      <div v-else class="daily-list">
        <div
          v-for="item in d.list"
          :key="item.report_date"
          class="daily-item"
          @click="openDailyDetail(item.report_date)"
        >
          <div class="daily-item-title">日报 {{ item.report_date }}</div>
          <div class="daily-item-sub">{{ subtitle(item) }}</div>
        </div>
      </div>

      <div v-if="d.msg" class="daily-msg">{{ d.msg }}</div>

      <div class="daily-pager" v-if="d.list.length">
        <button class="btn ghost" type="button" :disabled="d.page <= 0" @click="dailyPrevPage">上一页</button>
        <span class="daily-page-info">{{ pageInfo }}</span>
        <button class="btn ghost" type="button" :disabled="!d.hasMore" @click="dailyNextPage">下一页</button>
      </div>

      <div class="daily-footer">
        <button class="btn ghost" type="button" @click="closeDailyList">关闭</button>
      </div>
    </div>
  </div>
</template>
