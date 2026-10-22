<script setup>
import { computed } from "vue";
import { state } from "../store.js";

// 展示层聚合：用已加载的学习记录按上海时区统计每日打卡数，不新增任何接口请求
const WEEKS = 32;
const DAY_MS = 86400000;

function shanghaiKey(d) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(d);
}

function keyToUTC(key) {
  const [y, m, d] = key.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

const todayKey = shanghaiKey(new Date());

const countMap = computed(() => {
  const m = {};
  for (const r of state.records || []) {
    if (!r.created_at) continue;
    const t = new Date(r.created_at);
    if (Number.isNaN(t.getTime())) continue;
    const k = shanghaiKey(t);
    m[k] = (m[k] || 0) + 1;
  }
  return m;
});

const todayUTC = keyToUTC(todayKey);
// 周一为一周起始，最后一列是本周
const startUTC = new Date(
  todayUTC.getTime() - (((todayUTC.getUTCDay() + 6) % 7) + (WEEKS - 1) * 7) * DAY_MS
);

const weeks = computed(() => {
  const map = countMap.value;
  const cols = [];
  for (let w = 0; w < WEEKS; w++) {
    const col = [];
    for (let i = 0; i < 7; i++) {
      const day = new Date(startUTC.getTime() + (w * 7 + i) * DAY_MS);
      const key = day.toISOString().slice(0, 10);
      col.push({ key, count: key > todayKey ? null : map[key] || 0 });
    }
    cols.push(col);
  }
  return cols;
});

const activeDays = computed(() =>
  weeks.value.reduce((n, col) => n + col.filter((d) => d.count > 0).length, 0)
);

// 每列（周一）所属月份，仅在换月时显示标签，避免拥挤
const months = computed(() => {
  let last = -1;
  return weeks.value.map((col) => {
    const m = keyToUTC(col[0].key).getUTCMonth() + 1;
    if (m !== last) {
      last = m;
      return m + "月";
    }
    return "";
  });
});

const rangeText = computed(() => {
  const first = weeks.value[0]?.[0]?.key;
  if (!first) return "";
  const fmt = (k) => `${Number(k.slice(5, 7))}/${Number(k.slice(8, 10))}`;
  return `${fmt(first)} – ${fmt(todayKey)}`;
});

const weekCount = computed(() => {
  const last = weeks.value[weeks.value.length - 1] || [];
  return last.reduce((n, d) => n + (d.count || 0), 0);
});

const streak = computed(() => {
  const map = countMap.value;
  let day = todayUTC;
  if (!(map[todayKey] > 0)) day = new Date(day.getTime() - DAY_MS); // 今天还没打卡时从昨天算
  let n = 0;
  while (n < 366) {
    const k = day.toISOString().slice(0, 10);
    if (!(map[k] > 0)) break;
    n++;
    day = new Date(day.getTime() - DAY_MS);
  }
  return n;
});

function level(c) {
  if (!c) return 0;
  if (c >= 6) return 4;
  if (c >= 4) return 3;
  if (c >= 2) return 2;
  return 1;
}
</script>

<template>
  <section class="panel heatmap-panel">
    <div class="panel-head">
      <h2><span class="heat-ico"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.4-.5-2-1-3-1.1-2.1-.2-4 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.2.4-2.3 1-3a2.5 2.5 0 0 0 2.5 2.5z"/></svg></span>学习打卡</h2>
      <span class="heat-streak">连续 {{ streak }} 天</span>
    </div>
    <div class="heat-months">
      <span v-for="(m, i) in months" :key="i" class="heat-month">{{ m }}</span>
    </div>
    <div class="heat-grid">
      <div v-for="(col, w) in weeks" :key="w" class="heat-col">
        <span
          v-for="d in col"
          :key="d.key"
          class="heat-cell"
          :class="d.count === null ? 'heat-off' : 'lv' + level(d.count)"
          :title="d.count === null ? '' : d.key + ' · ' + d.count + ' 条记录'"
        ></span>
      </div>
    </div>
    <div class="heat-foot">
      <span>{{ rangeText }} · 打卡 {{ activeDays }} 天 · 本周 {{ weekCount }} 条</span>
      <span class="heat-legend">
        少<i class="heat-cell lv0"></i><i class="heat-cell lv1"></i><i class="heat-cell lv2"></i><i class="heat-cell lv3"></i><i class="heat-cell lv4"></i>多
      </span>
    </div>
  </section>
</template>
