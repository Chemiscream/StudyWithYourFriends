<script setup>
import { computed } from "vue";
import { state, openDailyList } from "../store.js";

const latest = computed(() => state.daily.latest);
const preview = computed(() => {
  const s = latest.value?.summary;
  if (!s) return "";
  const oneLine = s.replace(/\s+/g, " ").trim();
  return oneLine.length > 120 ? oneLine.slice(0, 120) + "…" : oneLine;
});
</script>

<template>
  <section class="panel daily-entry" @click="openDailyList">
    <div class="panel-head">
      <h2><span class="daily-ico">📅</span>AI 日报</h2>
      <span class="record-time" v-if="latest">{{ latest.report_date }} ›</span>
    </div>
    <p v-if="latest" class="daily-preview-text">{{ preview }}</p>
    <p v-else class="daily-hint">每天 0 点自动生成前一天的全站学习日报，看看大家都学了啥～</p>
  </section>
</template>
