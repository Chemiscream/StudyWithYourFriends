<script setup>
import { computed } from "vue";
import { state, closeDailyList, dailyPrevPage, dailyNextPage } from "../store.js";
import { mdRender } from "../utils/md.js";

const d = state.daily;
const pageInfo = computed(() => `第 ${d.page + 1} 页`);

function medal(i) { return i === 0 ? "🥇" : i === 1 ? "🥈" : i === 2 ? "🥉" : ""; }
</script>

<template>
  <div class="modal-mask" @click.self="closeDailyList">
    <div class="modal-card daily-list-card">
      <h2>📅 AI 日报</h2>

      <div v-if="d.loading" class="daily-loading">加载中…</div>
      <div v-else-if="!d.list.length" class="daily-empty">还没有日报</div>

      <div v-else class="daily-multi">
        <article v-for="item in d.list" :key="item.report_date" class="daily-day">
          <h3 class="daily-day-date">📅 {{ item.report_date }}</h3>

          <div v-if="item.summary" class="daily-summary" v-html="mdRender(item.summary)"></div>
          <div v-else class="daily-no-summary">（AI 摘要暂不可用）</div>

          <div class="daily-stat-block">
            <h3>📊 统计</h3>
            <p class="daily-total">共 <strong>{{ item.stat?.total || 0 }}</strong> 条学习记录</p>
          </div>

          <div v-if="item.stat?.ranking?.length" class="daily-stat-block">
            <h3>🏆 提交排行</h3>
            <div class="daily-rank">
              <div v-for="(r, i) in item.stat.ranking" :key="r.username" class="daily-rank-row">
                <span class="daily-rank-medal">{{ medal(i) }}</span>
                <span class="daily-rank-name" :style="{ color: '#' + r.color }">{{ r.username }}</span>
                <span class="daily-rank-count">{{ r.count }} 条</span>
              </div>
            </div>
          </div>

          <div v-if="item.stat?.words?.length" class="daily-stat-block">
            <h3>🔥 今日热词</h3>
            <div class="daily-words">
              <span v-for="w in item.stat.words" :key="w.text" class="daily-word">{{ w.text }} <em>{{ w.weight }}</em></span>
            </div>
          </div>
        </article>
      </div>

      <div v-if="d.msg" class="daily-msg">{{ d.msg }}</div>

      <!-- 列表按日期倒序：上一页=更新(page--)，下一页=更早(page++) -->
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
