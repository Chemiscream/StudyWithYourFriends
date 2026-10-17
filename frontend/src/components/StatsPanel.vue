<script setup>
import { ref, onMounted, watch, nextTick } from "vue";
import { state, loadStats } from "../store.js";

// 折叠状态存 localStorage（与旧版 key 一致）
const collapsed = ref(localStorage.getItem("studylog_collapse_stats") === "1");
const cloudCanvas = ref(null);

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

const CLOUD_COLORS = ["#4a90d9","#e74c3c","#27ae60","#f39c12","#9b59b6","#1abc9c","#e67e22","#16a085","#d35400","#8e44ad","#2980b9","#c0392b"];
const maxOf = () => Math.max(...state.ranking.map(r => r.count), 1);

function drawCloud() {
  const cv = cloudCanvas.value;
  if (!cv) return;
  const words = state.words;
  const dpr = window.devicePixelRatio || 1;
  const W = cv.clientWidth || 600, H = 320;
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
    const size = 14 + t * 34; // 14~48px
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
      ctx.fillStyle = CLOUD_COLORS[i % CLOUD_COLORS.length];
      ctx.font = `700 ${size}px "Microsoft YaHei", sans-serif`;
      ctx.textAlign = "center"; ctx.textBaseline = "middle";
      ctx.fillText(w.text, x, y);
    }
  }
}

function medal(i) { return i === 0 ? "🥇 " : i === 1 ? "🥈 " : i === 2 ? "🥉 " : ""; }
function pct(i) { return Math.max(4, Math.round(state.ranking[i].count / maxOf() * 100)); }

watch(() => state.words, () => nextTick(drawCloud));
onMounted(() => { if (!collapsed.value) nextTick(drawCloud); });
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
        <div v-if="!state.ranking.length" class="rank-empty">暂无数据</div>
        <div v-for="(r, i) in state.ranking" :key="r.username" class="rank-row">
          <span class="rank-name" :style="{ color: '#' + r.color }">{{ medal(i) }}{{ r.username }}</span>
          <div class="rank-bar-wrap"><div class="rank-bar" :style="{ width: pct(i) + '%', background: '#' + r.color }"></div></div>
          <span class="rank-count">{{ r.count }} 条</span>
        </div>
      </div>
      <h3 class="stats-sub">关键词词云</h3>
      <canvas ref="cloudCanvas" class="cloud-canvas"></canvas>
    </div>
  </section>
</template>
