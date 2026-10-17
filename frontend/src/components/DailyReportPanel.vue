<script setup>
import { computed } from "vue";
import { state, openDailyList, openDailyDetail } from "../store.js";

const latest = computed(() => state.daily.latest);

// 副标题：summary 前 20 字，没有则用统计
const subtitle = computed(() => {
  const r = latest.value;
  if (!r) return "";
  const s = r.summary;
  if (s && s.trim()) {
    const one = s.replace(/\s+/g, " ").trim();
    return one.length > 20 ? one.slice(0, 20) + "…" : one;
  }
  return `共 ${r.stat?.total || 0} 条记录`;
});

function openToday() {
  if (latest.value) openDailyDetail(latest.value.report_date);
}
function openMore(e) {
  e.stopPropagation();
  openDailyList();
}
</script>

<template>
  <section class="panel daily-entry">
    <div class="panel-head">
      <h2><span class="daily-ico">📅</span>AI 日报</h2>
      <span class="daily-more" v-if="latest" @click="openMore">
        {{ latest.report_date }} · 查看更多
      </span>
    </div>

    <div v-if="latest" class="daily-card" @click="openToday">
      <div class="daily-card-title">日报 {{ latest.report_date }}</div>
      <div class="daily-card-sub">{{ subtitle }}</div>
    </div>
    <p v-else class="daily-hint">每天 0 点自动生成前一天的全站学习日报，看看大家都学了啥～</p>
  </section>
</template>
