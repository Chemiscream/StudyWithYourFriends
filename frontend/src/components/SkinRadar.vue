<script setup>
import { computed } from "vue";

/**
 * 皮肤专用五维图：白天是玻璃雷达，夜间是 HUD 扫描盘。
 * 与原有五角星是两套完全不同的图形语言。
 */
const props = defineProps({ stats: { type: Array, default: () => [] } });

const CX = 160;
const CY = 158;
const R = 108;
const RINGS = [0.2, 0.4, 0.6, 0.8, 1];

function ptAt(i, ratio) {
  const a = ((-90 + i * 72) * Math.PI) / 180;
  return [CX + Math.cos(a) * R * ratio, CY + Math.sin(a) * R * ratio];
}

function poly(ratio) {
  return RINGS.map((_, i) => ptAt(i, ratio).map((n) => n.toFixed(1)).join(",")).join(" ");
}
const ringPolys = RINGS.map((r) => poly(r));

// 等级内的进度也计入，避免 LV.1 时图形缩成一点
const ratios = computed(() => props.stats.map((s) => {
  const rank = Math.min(5, Math.max(1, s.rank || 1));
  const within = Math.max(0, Math.min(100, s.progress || 0)) / 100;
  return Math.max(0.16, Math.min(1, (rank - 1 + within) / 5));
}));

const dataPoly = computed(() => ratios.value.map((r, i) => ptAt(i, r).map((n) => n.toFixed(1)).join(",")).join(" "));
const dots = computed(() => ratios.value.map((r, i) => {
  const [x, y] = ptAt(i, r);
  return { x, y, key: props.stats[i]?.key || i };
}));
const labels = computed(() => props.stats.map((s, i) => {
  const [x, y] = ptAt(i, 1.26);
  const anchor = x < CX - 12 ? "end" : x > CX + 12 ? "start" : "middle";
  return { key: s.key || i, name: s.name || "", rank: s.rank || 1, max: s.rank >= 5, x, y, anchor };
}));
</script>

<template>
  <svg class="skin-radar" viewBox="0 0 320 320" role="img" aria-label="五维能力雷达图">
    <defs>
      <radialGradient id="skinRadarFill" cx="50%" cy="50%" r="50%">
        <stop class="sr-stop-a" offset="0%" />
        <stop class="sr-stop-b" offset="100%" />
      </radialGradient>
    </defs>
    <circle class="sr-hub" :cx="CX" :cy="CY" :r="R + 8" />
    <polygon
      v-for="(p, i) in ringPolys" :key="'r' + i"
      :class="i === ringPolys.length - 1 ? 'sr-ring sr-ring-out' : 'sr-ring'"
      :points="p"
    />
    <line
      v-for="(s, i) in stats" :key="'s' + i" class="sr-spoke"
      :x1="CX" :y1="CY" :x2="ptAt(i, 1)[0].toFixed(1)" :y2="ptAt(i, 1)[1].toFixed(1)"
    />
    <polygon class="sr-fill" :points="dataPoly" />
    <polygon class="sr-line" :points="dataPoly" />
    <circle v-for="d in dots" :key="'d' + d.key" class="sr-dot" :cx="d.x" :cy="d.y" r="3.4" />
    <g v-for="l in labels" :key="'l' + l.key">
      <text class="sr-name" :x="l.x" :y="l.y" :text-anchor="l.anchor">{{ l.name }}</text>
      <text class="sr-rank" :x="l.x" :y="l.y + 12" :text-anchor="l.anchor">{{ l.max ? "MAX" : "LV." + l.rank }}</text>
    </g>
  </svg>
</template>
