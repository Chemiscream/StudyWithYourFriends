<script setup>
import { state, openDailyList } from "../store.js";
</script>

<template>
  <section class="panel daily-entry" @click="openDailyList">
    <div class="panel-head">
      <h2><span class="daily-ico">📅</span>AI 日报</h2>
      <span class="record-time">点击查看 ›</span>
    </div>
    <p class="daily-hint">每天 0 点自动生成前一天的全站学习日报，看看大家都学了啥～</p>
  </section>
</template>
