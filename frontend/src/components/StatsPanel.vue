<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from "vue";
import { state, loadStats } from "../store.js";

// 折叠状态存 localStorage（与旧版 key 一致）
const collapsed = ref(localStorage.getItem("studylog_collapse_stats") === "1");
const cloudCanvas = ref(null);

// 提交排行不展示 AI 虚拟用户；默认只露前三名，可展开
const AI_NAME = "AI助手";
const ranking = computed(() => state.ranking.filter((r) => r.username !== AI_NAME));
const showAllRanking = ref(false);
const visibleRanking = computed(() =>
  showAllRanking.value ? ranking.value : ranking.value.slice(0, 3)
);

const RANGES = [
  { key: "all", label: "全部" },
  { key: "month", label: "本月" },
  { key: "week", label: "本周" },
  { key: "today", label: "本日" },
];

function toggle() {
  collapsed.value = !collapsed.value;
  localStorage.setItem("studylog_collapse_stats", collapsed.value ? "1" : "0");
  if (!collapsed.value) nextTick(drawCloud);
}

function switchRange(key) {
  if (state.statsRange === key) return;
  loadStats(key);
}

const CLOUD_THEME = {
  light: ["#0099c0", "#123f5c", "#b97900", "#0a8f5f", "#bd3f7c", "#6148c9", "#3f7fa3"],
  dark: ["#ffffff", "#e60012", "#ffd400", "#f7f3e6", "#ff6b74", "#c9a86a", "#ff8f00"],
};
const maxOf = () => Math.max(...ranking.value.map(r => r.count), 1);

function drawCloud() {
  const cv = cloudCanvas.value;
  if (!cv) return;
  const words = state.words;
  const dark = document.documentElement.getAttribute("data-theme") === "dark";
  const colors = dark ? CLOUD_THEME.dark : CLOUD_THEME.light;
  const dpr = window.devicePixelRatio || 1;
  const W = cv.clientWidth || 600, H = cv.clientHeight || 320;
  cv.width = W * dpr; cv.height = H * dpr;
  const ctx = cv.getContext("2d");
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, W, H);
  if (!words.length) {
    ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue("--muted") || "#888";
    ctx.font = '14px "Microsoft YaHei", sans-serif';
    ctx.textAlign = "center"; ctx.textBaseline = "middle";
    ctx.fillText("暂无数据", W / 2, H / 2);
    return;
  }
  const max = words[0].weight, min = words[words.length - 1].weight;
  const placed = [];
  for (let i = 0; i < words.length; i++) {
    const w = words[i];
    const t = max === min ? 1 : (w.weight - min) / (max - min);
    const size = (H < 260 ? 8 : 14) + t * (H < 260 ? 14 : 34); // 侧栏矮容器同步缩小字号，能放下更多词
    ctx.font = `700 ${size}px "Microsoft YaHei", sans-serif`;
    const tw = ctx.measureText(w.text).width, th = size;
    let ok = false, x = W / 2, y = H / 2;
    let angle = Math.random() * Math.PI * 2;
    for (let step = 0; step < 800; step++) {
      const r = step * 1.1;
      angle += 0.35;
      x = W / 2 + r * Math.cos(angle) * 1.4;
      y = H / 2 + r * Math.sin(angle);
      const rect = { x: x - tw / 2 - 3, y: y - th / 2 - 3, w: tw + 6, h: th + 6 };
      if (rect.x < 0 || rect.y < 0 || rect.x + rect.w > W || rect.y + rect.h > H) continue;
      let hit = false;
      for (const p of placed) {
        if (rect.x < p.x + p.w && p.x < rect.x + rect.w && rect.y < p.y + p.h && p.y < rect.y + rect.h) { hit = true; break; }
      }
      if (!hit) { placed.push(rect); ok = true; break; }
    }
    if (ok) {
      ctx.fillStyle = colors[i % colors.length];
      ctx.globalAlpha = 0.6 + t * 0.4;
      ctx.font = `700 ${size}px "Microsoft YaHei", sans-serif`;
      ctx.textAlign = "center"; ctx.textBaseline = "middle";
      ctx.shadowColor = dark ? "rgba(230,0,18,.30)" : "rgba(0,140,180,.18)";
      ctx.shadowBlur = size > 24 ? 12 : 6;
      ctx.fillText(w.text, x, y);
      ctx.shadowBlur = 0;
      ctx.globalAlpha = 1;
    }
  }
}

function medal(i) { return i === 0 ? "🥇 " : i === 1 ? "🥈 " : i === 2 ? "🥉 " : ""; }
function pct(r) { return Math.max(4, Math.round(r.count / maxOf() * 100)); }

watch(() => state.words, () => nextTick(drawCloud));
watch(() => state.statsRange, () => nextTick(drawCloud));
function onTheme() { nextTick(drawCloud); }
// 右栏高度随视口伸缩，窗口尺寸变化后重绘，避免画布拉伸模糊
let resizeTimer = null;
function onResize() {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(drawCloud, 160);
}
onMounted(() => {
  window.addEventListener("studylog:theme", onTheme);
  window.addEventListener("resize", onResize);
  if (!collapsed.value) nextTick(drawCloud);
});
onUnmounted(() => {
  window.removeEventListener("studylog:theme", onTheme);
  window.removeEventListener("resize", onResize);
  clearTimeout(resizeTimer);
});
</script>

<template>
  <section class="panel" :class="{ collapsed }">
    <div class="panel-head collapse-head" @click="toggle">
      <h2><span class="chev">▼</span>学习统计</h2>
      <span class="record-time">共 {{ state.statsTotal }} 条记录</span>
    </div>
    <div class="collapse-body">
      <div class="stats-range">
        <button
          v-for="r in RANGES"
          :key="r.key"
          type="button"
          class="range-btn"
          :class="{ active: state.statsRange === r.key }"
          @click="switchRange(r.key)"
        >{{ r.label }}</button>
      </div>
      <h3 class="stats-sub">提交排行</h3>
      <div class="rank-list">
        <div v-if="!ranking.length" class="rank-empty">暂无数据</div>
        <div v-for="(r, i) in visibleRanking" :key="r.username" class="rank-row">
          <span class="rank-name" :style="{ color: '#' + r.color }">{{ medal(i) }}{{ r.username }}</span>
          <div class="rank-bar-wrap"><div class="rank-bar" :style="{ width: pct(r) + '%', background: '#' + r.color }"></div></div>
          <span class="rank-count">{{ r.count }} 条</span>
        </div>
      </div>
      <button v-if="ranking.length > 3" type="button" class="rank-more" @click="showAllRanking = !showAllRanking">
        {{ showAllRanking ? '收起排名' : '查看更多（共 ' + ranking.length + ' 人）' }}
      </button>
      <h3 class="stats-sub">关键词词云</h3>
      <canvas ref="cloudCanvas" class="cloud-canvas"></canvas>
    </div>
  </section>
</template>
