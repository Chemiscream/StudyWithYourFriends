<script setup>
import { computed } from "vue";
import { state, closeDailyList, dailyPrevPage, dailyNextPage, openDailyDetail } from "../store.js";

const d = state.daily;
const pageInfo = computed(() => `第 ${d.page + 1} 页`);
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
          <div class="daily-item-head">
            <span class="daily-date">{{ item.report_date }}</span>
            <span class="daily-count">{{ item.total }} 条记录</span>
          </div>
          <p class="daily-preview">
            {{ item.summary_preview || (item.has_summary ? "" : "（该日无 AI 摘要）") }}
          </p>
        </div>
      </div>

      <div v-if="d.msg" class="daily-msg">{{ d.msg }}</div>

      <!-- 列表按日期倒序：上一天=更早(page++)，下一天=更新(page--) -->
      <div class="daily-pager" v-if="d.list.length">
        <button class="btn ghost" type="button" :disabled="!d.hasMore" @click="dailyNextPage">上一天</button>
        <span class="daily-page-info">{{ pageInfo }}</span>
        <button class="btn ghost" type="button" :disabled="d.page <= 0" @click="dailyPrevPage">下一天</button>
      </div>

      <div class="daily-footer">
        <button class="btn ghost" type="button" @click="closeDailyList">关闭</button>
      </div>
    </div>
  </div>
</template>
