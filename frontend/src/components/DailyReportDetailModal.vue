<script setup>
import { computed } from "vue";
import { state, closeDailyDetail } from "../store.js";
import { mdRender, esc } from "../utils/md.js";

const detail = computed(() => state.daily.detail);
const stat = computed(() => detail.value?.stat || {});
const ranking = computed(() => stat.value.ranking || []);
const words = computed(() => stat.value.words || []);

function medal(i) { return i === 0 ? "🥇" : i === 1 ? "🥈" : i === 2 ? "🥉" : ""; }

// 下载日报为文本文件
function download() {
  const d = detail.value;
  if (!d) return;
  const lines = [];
  lines.push(`学习记录 AI 日报 — ${d.report_date}`);
  lines.push("=".repeat(30));
  lines.push("");
  if (d.summary) {
    lines.push("【AI 解读】");
    lines.push(d.summary);
    lines.push("");
  }
  lines.push(`【统计】共 ${stat.value.total || 0} 条记录`);
  lines.push("");
  if (ranking.value.length) {
    lines.push("【提交排行】");
    ranking.value.forEach((r, i) => lines.push(`${medal(i)} ${r.username}：${r.count} 条`));
    lines.push("");
  }
  if (words.value.length) {
    lines.push("【今日热词】");
    lines.push(words.value.map(w => `${w.text}(${w.weight})`).join("、"));
    lines.push("");
  }
  const blob = new Blob([lines.join("\n")], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `日报_${d.report_date}.txt`;
  a.click();
  URL.revokeObjectURL(url);
}
</script>

<template>
  <div class="modal-mask" @click.self="closeDailyDetail">
    <div class="modal-card daily-detail-card">
      <h2>📅 {{ detail?.report_date }} 日报</h2>

      <div v-if="detail?.summary" class="daily-summary" v-html="mdRender(detail.summary)"></div>
      <div v-else class="daily-no-summary">（AI 摘要暂不可用，可能是模型服务未启动）</div>

      <div class="daily-stat-block">
        <h3>📊 统计</h3>
        <p class="daily-total">共 <strong>{{ stat.total || 0 }}</strong> 条学习记录</p>
      </div>

      <div v-if="ranking.length" class="daily-stat-block">
        <h3>🏆 提交排行</h3>
        <div class="daily-rank">
          <div v-for="(r, i) in ranking" :key="r.username" class="daily-rank-row">
            <span class="daily-rank-medal">{{ medal(i) }}</span>
            <span class="daily-rank-name" :style="{ color: '#' + r.color }">{{ r.username }}</span>
            <span class="daily-rank-count">{{ r.count }} 条</span>
          </div>
        </div>
      </div>

      <div v-if="words.length" class="daily-stat-block">
        <h3>🔥 今日热词</h3>
        <div class="daily-words">
          <span v-for="w in words" :key="w.text" class="daily-word">{{ w.text }} <em>{{ w.weight }}</em></span>
        </div>
      </div>

      <div class="daily-detail-actions">
        <button class="btn primary" type="button" @click="download">下载日报</button>
        <button class="btn ghost" type="button" @click="closeDailyDetail">关闭</button>
      </div>
    </div>
  </div>
</template>
