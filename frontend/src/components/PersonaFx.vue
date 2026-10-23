<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { state } from "../store.js";
import {
  PERSONA_ORDER as ORDER,
  PERSONA_NAMES as NAMES,
  PERSONA_TITLES as TITLES,
  PERSONA_THRESHOLDS as THRESH,
  p5FacetColor,
} from "../persona.js";

const CX = 150;
const CY = 150;
const IR_RATIO = 0.42;
// P3：中心小星 + 五个按等级变大的圆圈（与个人档案页同一套几何）
const P3_R = 30;
const P3_DIST = 62;
const P3_MIN = 44;
const P3_STEP = 6.5;
const P3_TEXT_OFFSET = [[0, 0], [0, 0], [6, 0], [-6, 0], [0, 0]];
// 称号条相对圆圈的位置（与档案页同一套）
const P3_TAG_OFFSET = [[0, -40], [0, 34], [10, 34], [-14, 34], [-4, 34]];
// P5：外框星 + 数据星
const FRAME_R = 112;
const DATA_R = FRAME_R * 0.8;
// P3 / P5 统一节奏：圆环收拢 → 闪光两下 →（RANK UP）圆圈长大 / 角伸长后再闪
const RING_MS = 850;
const FLASH_MS = 780;
const GROW_MS = 550;
const RING_DELAY = 400;
const STEP_DELAY = 200;

function ptAt(deg, radius) {
  const a = (deg * Math.PI) / 180;
  return [CX + Math.cos(a) * radius, CY + Math.sin(a) * radius];
}
function starPoints(list, maxR) {
  const pts = [];
  for (let i = 0; i < 5; i++) {
    const outer = maxR * list[i];
    const inner = IR_RATIO * maxR * ((list[i] + list[(i + 1) % 5]) / 2);
    pts.push(ptAt(-90 + i * 72, outer));
    pts.push(ptAt(-90 + i * 72 + 36, inner));
  }
  return pts;
}
function toPoly(pts) {
  return pts.map((p) => p.map((n) => n.toFixed(1)).join(",")).join(" ");
}
function ratioOfValue(v) {
  return 0.35 + 0.65 * Math.min(1, Math.sqrt(Math.max(0, v) / 700));
}
function ratioOfRank(rank) {
  return ratioOfValue(THRESH[Math.min(5, Math.max(1, rank)) - 1] ?? 0);
}

const p3Star = toPoly(starPoints([1, 1, 1, 1, 1], P3_R));
const frameStar = toPoly(starPoints([1, 1, 1, 1, 1], FRAME_R));
// 与档案页一致：外框内凹处小圆点，数据星内凹处深色小圆点
const frameDots = starPoints([1, 1, 1, 1, 1], FRAME_R).filter((_, i) => i % 2 === 1);
// 称号左侧的蓝色小星标（与档案页同一个图形）
const P3_EMBLEM = Array.from({ length: 10 }, (_, i) => {
  const a = (-90 + i * 36) * Math.PI / 180;
  const r = i % 2 ? 2.5 : 6;
  return [Math.cos(a) * r, Math.sin(a) * r];
}).map((p) => p.map((n) => n.toFixed(1)).join(",")).join(" ");
function titleOf(key, rank) {
  const list = TITLES[key];
  if (!list) return "";
  return list[Math.min(5, Math.max(1, rank)) - 1] || "";
}

const isRank = computed(() => state.rankFx.show);
const show = computed(() => state.rankFx.show || state.pointFx.show);
const fx = computed(() => (isRank.value ? state.rankFx : state.pointFx));
const changed = computed(() => (isRank.value ? state.rankFx.items : state.pointFx.gains) || []);
const statMap = computed(() => Object.fromEntries((state.fxStats || []).map((s) => [s.key, s])));
const changedMap = computed(() => Object.fromEntries(changed.value.map((c) => [c.key, c])));
const orderIdx = computed(() => Object.fromEntries(changed.value.map((c, i) => [c.key, i])));

// 数据还没回来时先撑住动画，最多等 900ms，避免网络异常时什么都不显示
const fallback = ref(false);
const ready = computed(() => (state.fxStats || []).length === 5 || fallback.value);

// RANK UP 时圆圈先保持旧等级大小，等金色闪光结束后再长大（同时把数字换成新等级）
const grown = ref([true, true, true, true, true]);
// 切换到新的动画内容时强制重挂 SVG，让 CSS 关键帧从头播放
const fxKey = ref(0);

const dots = computed(() => ORDER.map((k, i) => {
  const s = statMap.value[k] || {};
  const ch = changedMap.value[k];
  const rank = ch && isRank.value ? ch.rank : (s.rank || 1);
  const fromRank = ch && isRank.value ? ch.from : rank;
  const isUp = !!(ch && isRank.value && rank > fromRank);
  const shownRank = isUp && !grown.value[i] ? fromRank : rank;
  const r = P3_MIN + (shownRank - 1) * P3_STEP;
  const rTo = P3_MIN + (rank - 1) * P3_STEP;
  const rFrom = P3_MIN + (fromRank - 1) * P3_STEP;
  const pt = ptAt(-90 + i * 72, P3_DIST);
  const lab = ptAt(-90 + i * 72, P3_DIST + 8);
  const [ox, oy] = P3_TAG_OFFSET[i];
  const idx = ch ? orderIdx.value[k] : 0;
  return {
    key: k,
    x: pt[0], y: pt[1],
    lx: lab[0] + P3_TEXT_OFFSET[i][0], ly: lab[1] + P3_TEXT_OFFSET[i][1],
    tx: lab[0] + ox, ty: lab[1] + oy,
    rank: shownRank, r,
    // 圆环 / 闪光贴着当前圆圈大小，长大发生在它们之后
    rFx: isUp && !grown.value[i] ? rFrom : r,
    numSize: r * 0.66, nameSize: r * 0.27,
    name: s.name || NAMES[k] || k,
    // 称号跟着圆圈的节奏走：RANK UP 时旧称号保持到圆圈长大那一刻再换成新称号
    title: isUp && !grown.value[i] ? titleOf(k, fromRank) : (s.title || titleOf(k, rank)),
    changed: !!ch,
    pendingGrow: isUp && !grown.value[i],
    grow: !!(isUp && grown.value[i]),
    scaleFrom: (rFrom / rTo).toFixed(3),
    delay: RING_DELAY + idx * STEP_DELAY,
    ratio: ratioOfValue(s.value || 0),
    fromRatio: isUp ? ratioOfRank(fromRank) : ratioOfValue(s.value || 0),
  };
}));


// P5：金色数据星从旧等级「伸长」到新等级，再让对应角闪几下
const animRatios = ref([0.35, 0.35, 0.35, 0.35, 0.35]);
const dataStar = computed(() => toPoly(starPoints(animRatios.value, DATA_R)));
const facets = computed(() => starPoints(animRatios.value, DATA_R).map((p, i, arr) => {
  const next = arr[(i + 1) % arr.length];
  return `${CX},${CY} ${p[0].toFixed(1)},${p[1].toFixed(1)} ${next[0].toFixed(1)},${next[1].toFixed(1)}`;
}));
const dataDots = computed(() => starPoints(animRatios.value, DATA_R).filter((_, i) => i % 2 === 1));
const dataDotR = computed(() => {
  const avg = animRatios.value.reduce((a, b) => a + b, 0) / 5;
  return Math.max(1.0, Math.min(1.9, avg * 1.9)).toFixed(2);
});
const corners = computed(() => ORDER.map((k, i) => {
  const d = dots.value[i];
  const ratio = animRatios.value[i];
  const avgPrev = (ratio + animRatios.value[(i + 4) % 5]) / 2;
  const avgNext = (ratio + animRatios.value[(i + 1) % 5]) / 2;
  const outer = ptAt(-90 + i * 72, DATA_R * ratio);
  const p1 = ptAt(-90 + i * 72 + 36, IR_RATIO * DATA_R * avgNext);
  const p2 = ptAt(-90 + i * 72 - 36, IR_RATIO * DATA_R * avgPrev);
  return {
    changed: d.changed,
    points: [outer, p1, p2].map((p) => p.map((n) => n.toFixed(1)).join(",")).join(" "),
    delay: 1100 + (d.changed ? orderIdx.value[k] : 0) * 140,
  };
}));
// 星图四周的标签，完全复用档案页的五维标签样式（名称 + 等级 + 称号）
const hasStats = computed(() => (state.fxStats || []).length === 5);
const labels = computed(() => ORDER.map((k) => {
  const s = statMap.value[k] || {};
  return { key: k, name: s.name || NAMES[k] || k, rank: s.rank || 1, title: s.title || "" };
}));

let raf = 0;
let timers = [];
function clearAnim() {
  cancelAnimationFrame(raf);
  timers.forEach(clearTimeout);
  timers = [];
}
/** RANK UP 的维度先按旧等级大小显示，等动画播放到「长大」那一刻再切到新等级。 */
function resetGrown() {
  grown.value = ORDER.map((k) => {
    const ch = changedMap.value[k];
    return !(ch && isRank.value && ch.rank > ch.from);
  });
}
function startAnim() {
  clearAnim();
  resetGrown();
  const list = dots.value;

  // P3：圆环收拢 → 圆内闪两下 →（RANK UP）圆圈长大并换成新等级数字
  list.forEach((d, i) => {
    if (!d.pendingGrow) return;
    timers.push(setTimeout(() => {
      const arr = grown.value.slice();
      arr[i] = true;
      grown.value = arr;
    }, d.delay + RING_MS + FLASH_MS + 60));
  });

  // P5：RANK UP 的角先伸长到当前等级，再闪烁；普通加分直接闪烁
  animRatios.value = list.map((d) => d.fromRatio);
  if (!list.some((d) => d.pendingGrow)) {
    animRatios.value = list.map((d) => d.ratio);
    return;
  }
  timers.push(setTimeout(() => {
    const targets = list.map((d) => d.ratio);
    const starts = list.map((d) => d.fromRatio);
    const t0 = performance.now();
    const step = (t) => {
      const p = Math.min(1, (t - t0) / 620);
      const e = 1 - Math.pow(1 - p, 3);
      animRatios.value = list.map((d, i) => (d.pendingGrow ? starts[i] + (targets[i] - starts[i]) * e : targets[i]));
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
  }, 420));
}

const sig = computed(() => `${isRank.value ? "R" : "P"}|` +
  (changed.value || []).map((c) => `${c.key}:${c.delta ?? c.rank}`).join(","));

watch(show, (v) => {
  clearAnim();
  fallback.value = false;
  if (!v) return;
  fxKey.value++;
  timers.push(setTimeout(() => { fallback.value = true; }, 900));
  if (ready.value) startAnim();
}, { immediate: true });

watch(ready, (v) => {
  if (v && show.value) startAnim();
});

// 动画播放期间又来了新的加分 / 升级：整块重挂，让动画从头播一遍
watch(sig, () => {
  if (!show.value) return;
  fxKey.value++;
  startAnim();
});

// 实时五维数据晚到（先用了上一次的数据）时，用新数据重播一次，避免圆圈大小/星角对不上
const statsSig = computed(() => (state.fxStats || []).map((s) => `${s.key}:${s.value}:${s.rank}`).join(","));
watch(statsSig, () => {
  if (!show.value) return;
  fxKey.value++;
  startAnim();
});

onBeforeUnmount(clearAnim);
</script>

<template>
  <transition name="pfx">
    <div v-if="show" class="pfx" role="status" aria-live="polite">
      <div class="pfx-glow"></div>
      <div class="pfx-body">
        <div class="pfx-head">
          <span class="pfx-tag">{{ fx.pending ? "WELCOME BACK" : "SOCIAL STATS" }}</span>
          <b class="pfx-title">{{ isRank ? "RANK UP" : "POINT UP" }}</b>
        </div>

        <div v-if="ready" class="pfx-starwrap">
          <svg :key="fxKey" class="pfx-chart" viewBox="0 0 300 300" aria-hidden="true">
          <defs>
            <!-- 与档案页一致的深灰底盘与赤道阴影 -->
            <linearGradient id="pfxFrame" gradientUnits="userSpaceOnUse" x1="40" y1="30" x2="250" y2="280">
              <stop offset="0" stop-color="#454545" />
              <stop offset="1" stop-color="#2c2c2c" />
            </linearGradient>
            <radialGradient id="pfxCore">
              <stop offset="0" stop-color="#8a4c00" stop-opacity=".42" />
              <stop offset="1" stop-color="#8a4c00" stop-opacity="0" />
            </radialGradient>
          </defs>

          <!-- P3（浅色）：圆圈 → 从画面外收进来的圆环 → 圈内闪光两次 →（RANK UP）圆圈长大 -->
          <g class="pfx-p3">
            <circle
              v-for="(c, i) in dots" :key="'c' + i"
              class="pfx-circle" :class="{ grow: c.grow }"
              :cx="c.x" :cy="c.y" :r="c.r"
              :style="c.grow ? { '--from': c.scaleFrom, transformOrigin: `${c.x}px ${c.y}px` } : null"
            />
            <circle
              v-for="(c, i) in dots" v-show="c.changed" :key="'r' + i"
              class="pfx-ring" :cx="c.x" :cy="c.y" :r="c.rFx"
              :style="{ animationDelay: c.delay + 'ms', transformOrigin: `${c.x}px ${c.y}px` }"
            />
            <circle
              v-for="(c, i) in dots" v-show="c.changed" :key="'f' + i"
              class="pfx-flash" :class="{ gold: isRank }" :cx="c.x" :cy="c.y" :r="c.rFx"
              :style="{ animationDelay: (c.delay + RING_MS + 30) + 'ms' }"
            />
            <polygon class="pfx-p3-star" :points="p3Star" />
            <g v-for="(c, i) in dots" :key="'n' + i" :transform="`rotate(-10 ${c.lx} ${c.ly})`">
              <text class="pfx-num" :font-size="c.numSize" :x="c.lx - 8" :y="c.ly + 15" text-anchor="end">{{ c.rank }}</text>
              <text class="pfx-name" :font-size="c.nameSize" :x="c.lx + 2" :y="c.ly + 14">{{ c.name }}</text>
            </g>
            <!-- 称号：与档案页同款（深青字 + 白描边 + 蓝色星标），RANK UP 时随圆圈长大换成新称号 -->
            <g
              v-for="(c, i) in dots" v-show="hasStats" :key="'t' + i"
              class="p3-tag" :class="{ flip: c.grow }" :transform="`rotate(-8 ${c.tx} ${c.ty})`"
            >
              <polygon class="p3-tag-star" :points="P3_EMBLEM" :transform="`translate(${c.tx - 31} ${c.ty - 1})`" />
              <text class="p3-tag-txt" :x="c.tx - 19" :y="c.ty + 4">{{ c.title }}</text>
            </g>
          </g>

          <!-- P5（深色）：先出现星图，对应角伸长到新等级后闪烁几下 -->
          <g class="pfx-p5">
            <polygon class="pfx-frame" :points="frameStar" />
            <circle
              v-for="(d, i) in frameDots" :key="'fd' + i"
              class="pfx-frame-dot" :cx="d[0]" :cy="d[1]" r="1.8"
            />
            <polygon
              v-for="(f, i) in facets" :key="'fa' + i"
              class="pfx-facet" :fill="p5FacetColor(i)" :points="f"
            />
            <circle cx="150" cy="150" r="26" fill="url(#pfxCore)" />
            <polygon class="pfx-outline" :points="dataStar" />
            <circle
              v-for="(d, i) in dataDots" :key="'dd' + i"
              class="pfx-data-dot" :cx="d[0]" :cy="d[1]" :r="dataDotR"
            />
            <polygon
              v-for="(c, i) in corners" v-show="c.changed" :key="'k' + i"
              class="pfx-corner" :points="c.points"
              :style="{ animationDelay: c.delay + 'ms' }"
            />
          </g>
          </svg>

          <div
            v-for="(s, i) in labels" v-show="hasStats" :key="'lb' + s.key"
            class="star-tag" :class="'st-' + s.key"
            :style="{ animationDelay: 100 + i * 45 + 'ms' }"
          >
            <span class="st-label">
              <b>{{ s.name }}</b>
              <em>{{ s.rank >= 5 ? "MAX" : "LV." + s.rank }}</em>
            </span>
            <small>{{ s.title }}</small>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>
