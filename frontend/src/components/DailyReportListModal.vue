<script setup>
import { computed } from "vue";
import { state, closeDailyList, dailyPrevPage, dailyNextPage, openDailyDetail, generateDailyReport } from "../store.js";

const d = state.daily;
const pageInfo = computed(() => `第 ${d.page + 1} 页`);

function yesterday() {
  const d = new Date(); d.setDate(d.getDate() - 1);
  return d.toISOString().slice(0, 10);
}
function dayBeforeYesterday() {
  const d = new Date(); d.setDate(d.getDate() - 2);
  return d.toISOString().slice(0, 10);
}
async function gen(date) { await generateDailyReport(date); }
</script>

<template>
  <div class="modal-mask" @click.self="closeDailyList">
    <div class="modal-card daily-list-card">
      <h2>📅 AI 日报</h2>
      <div v-if="d.loading" class="daily-loading">加载中…</div>
      <div v-else-if="!d.list.length" class="daily-empty">
        还没有日报。点击下方按钮补生成历史日报～
      </div>
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

      <div class="daily-pager" v-if="d.list.length">
        <button class="btn ghost" type="button" :disabled="d.page <= 0" @click="dailyPrevPage">上一页</button>
        <span class="daily-page-info">{{ pageInfo }}</span>
        <button class="btn ghost" type="button" :disabled="!d.hasMore" @click="dailyNextPage">下一页</button>
      </div>

      <div class="daily-backfill">
        <span class="daily-backfill-label">补生成：</span>
        <button class="btn ghost mini-btn" type="button" @click="gen(yesterday())">昨天</button>
        <button class="btn ghost mini-btn" type="button" @click="gen(dayBeforeYesterday())">前天</button>
      </div>

      <button class="btn ghost" type="button" style="margin-top:12px;width:100%;" @click="closeDailyList">关闭</button>
    </div>
  </div>
</template>
