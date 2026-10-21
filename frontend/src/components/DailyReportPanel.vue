<script setup>
import { computed } from "vue";
import { state, openDailyList, openDailyDetail } from "../store.js";
import { mdRender } from "../utils/md.js";

// expanded=true：移动端「日报」独立分页，直接铺开最新日报正文（数据与详情弹窗完全一致，不额外请求）
const props = defineProps({
  expanded: { type: Boolean, default: false },
});

const latest = computed(() => state.daily.latest);
const stat = computed(() => latest.value?.stat || {});
const ranking = computed(() => (stat.value.ranking || []).filter((r) => r.username !== "AI助手"));
const words = computed(() => stat.value.words || []);

// 副标题：优先用后端 subtitle，没有则用 summary 前 20 字兜底
const subtitle = computed(() => {
  const r = latest.value;
  if (!r) return "";
  if (r.subtitle) return r.subtitle;
  const s = r.summary;
  if (s && s.trim()) {
    const one = s.replace(/\s+/g, " ").trim();
    return one.length > 20 ? one.slice(0, 20) + "…" : one;
  }
  return `共 ${r.stat?.total || 0} 条记录`;
});

function medal(i) { return i === 0 ? "🥇 " : i === 1 ? "🥈 " : i === 2 ? "🥉 " : ""; }

function openToday() {
  if (latest.value) openDailyDetail(latest.value.report_date);
}
function openMore(e) {
  e.stopPropagation();
  openDailyList();
}
</script>

<template>
  <section class="panel daily-entry" :class="{ 'daily-entry-lg': expanded }">
    <div class="panel-head">
      <h2><span class="daily-ico">📅</span>AI 日报</h2>
      <span class="daily-more" v-if="latest" @click="openMore">
        {{ expanded ? '查看更多' : latest.report_date + ' · 查看更多' }}
      </span>
    </div>

    <template v-if="expanded">
      <div v-if="latest" class="daily-hero">
        <div class="daily-hero-date"><span>日报</span><b>{{ latest.report_date }}</b></div>

        <div v-if="latest.summary" class="daily-summary daily-hero-summary" v-html="mdRender(latest.summary)"></div>
        <div v-else class="daily-no-summary">（AI 摘要暂不可用，可能是模型服务未启动）</div>

        <div class="daily-hero-nums">
          <div class="daily-num">
            <strong>{{ stat.total || 0 }}</strong>
            <span>条学习记录</span>
          </div>
          <div class="daily-num">
            <strong>{{ ranking.length }}</strong>
            <span>位同学参与</span>
          </div>
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
      </div>
      <p v-else class="daily-hint">每天 0 点自动生成前一天的全站学习日报，看看大家都学了啥～</p>
    </template>

    <template v-else>
      <div v-if="latest" class="daily-card" @click="openToday">
        <div class="daily-card-title"><span>日报</span><b>{{ latest.report_date }}</b></div>
        <div class="daily-card-sub">{{ subtitle }}</div>
      </div>
      <p v-else class="daily-hint">每天 0 点自动生成前一天的全站学习日报，看看大家都学了啥～</p>
    </template>
  </section>
</template>
