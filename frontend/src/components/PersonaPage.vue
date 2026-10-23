<script setup>
import { computed, ref } from "vue";
import { state, closePersona, openDetail, backFromPersona } from "../store.js";
import StudyHeatmap from "./StudyHeatmap.vue";
import {
  PERSONA_ORDER as ORDER,
  PERSONA_TITLES,
  PERSONA_THRESHOLDS,
  PERSONA_RULES,
  PERSONA_COMMON_RULES,
  p5FacetColor,
  p5CoreColor,
  GOLD_RATIO,
  p5ArmRatio,
} from "../persona.js";

const rulesOpen = ref(false);

const p = computed(() => state.persona);
const data = computed(() => p.value.data);
const stats = computed(() => data.value?.stats || []);
const summary = computed(() => data.value?.summary || {});
const statMap = computed(() => Object.fromEntries(stats.value.map((s) => [s.key, s])));

const CX = 150;
const CY = 150;
const FRAME_R = 112;             // 外框五角星半径（四周留出贴纸标签的空间）
const DATA_R = FRAME_R * 0.8;    // 数据星满值半径：原作 MAX 时几乎铺满灰色底盘，只留一圈窄边
const IR_RATIO = 0.42;           // 五角星内凹半径 / 外角半径

function ptAt(deg, radius) {
  const a = deg * Math.PI / 180;
  return [CX + Math.cos(a) * radius, CY + Math.sin(a) * radius];
}

// 五维星图：星臂长度按等级均分——等级一只有黄金小星，之后每升一级
// 暗金角往外多伸四分之一，满级几乎铺满灰色底盘
const ratios = computed(() => ORDER.map((k) => p5ArmRatio(statMap.value[k]?.rank)));

/** 外角与内凹点交替，按五个维度数值生成不规则五角星 */
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

// 外框：满值五角星，原作里是一整块深灰色星形底盘
const framePts = starPoints([1, 1, 1, 1, 1], FRAME_R);
const frameStar = toPoly(framePts);
const frameDots = framePts.filter((_, i) => i % 2 === 1);
// 数据星：从中心切成 10 个三角面，明暗交替做出原作的立体折面
const dataPts = computed(() => starPoints(ratios.value, DATA_R));
const dataStar = computed(() => toPoly(dataPts.value));
const starFacets = computed(() => dataPts.value.map((p, i) => {
  const next = dataPts.value[(i + 1) % dataPts.value.length];
  return `${CX},${CY} ${p[0].toFixed(1)},${p[1].toFixed(1)} ${next[0].toFixed(1)},${next[1].toFixed(1)}`;
}));
// 中心金星 = 等级一时的星形，大小固定、不随等级变化；
// 之后每升一级，暗金色的星角再从这颗金星外侧继续伸长，
// 所以等级只有 1 的角根本没有外侧暗金部分
const goldPts = starPoints([GOLD_RATIO, GOLD_RATIO, GOLD_RATIO, GOLD_RATIO, GOLD_RATIO], DATA_R);
const goldStar = toPoly(goldPts);
const goldFacets = computed(() => goldPts.map((p, i) => {
  const next = goldPts[(i + 1) % goldPts.length];
  return `${CX},${CY} ${p[0].toFixed(1)},${p[1].toFixed(1)} ${next[0].toFixed(1)},${next[1].toFixed(1)}`;
}));
// 原作只在内凹处点小圆点，不标外角；圆点半径跟着星的大小缩放，
// 固定半径在小星上会变成几个深色圆斑，看起来像星星被咬掉了角
const starDots = computed(() => dataPts.value.filter((_, i) => i % 2 === 1));
const starDotR = computed(() => {
  const avg = ratios.value.reduce((a, b) => a + b, 0) / 5;
  return Math.max(1.0, Math.min(1.9, avg * 1.9));
});

// P3（浅色）：参考 P3R「POINT UP!」个人能力值界面——中心是一个固定大小的小五角星，
// 五个半透明圆圈绕中心均匀分布并互相叠加，**等级越高圆圈越大**（位置固定，尺寸随等级增长）；
// 每个圆圈下方挂一条黑底斜条，写着当前等级称号（原作里是「气场十足」这类黑条）
const P3_R = 30;                       // 中心小星：固定大小，不随数值变化
const P3_DIST = 62;                    // 圆心到中心的固定距离：等级只改半径，不改位置
const P3_CIRCLE_MIN = 44;              // LV.1 的圆圈半径
const P3_CIRCLE_STEP = 6.5;            // 每升一级，半径增加量（LV.5 = 70）
const p3Star = toPoly(starPoints([1, 1, 1, 1, 1], P3_R));
// 五条称号黑条按角位微调偏移：知识在正上方，黑条挂在圆圈上沿（不压住圈内数字）；
// 其余四条挂在各自圆圈下方，底部两条左右错开避免叠在一起
const P3_TAG_OFFSET = [[0, -40], [0, 34], [10, 34], [-14, 34], [-4, 34]];
// 底部两条（灵巧/体贴）数字名称向左右各让开一点，满级时不会互相顶到一起
const P3_TEXT_OFFSET = [[0, 0], [0, 0], [6, 0], [-6, 0], [0, 0]];
const p3Dots = computed(() => ORDER.map((k, i) => {
  const s = statMap.value[k] || {};
  const rank = Math.min(5, Math.max(1, s.rank || 1));
  const r = P3_CIRCLE_MIN + (rank - 1) * P3_CIRCLE_STEP;
  const pt = ptAt(-90 + i * 72, P3_DIST);
  const lab = ptAt(-90 + i * 72, P3_DIST + 8);
  const [ox, oy] = P3_TAG_OFFSET[i];
  const [tx2, ty2] = P3_TEXT_OFFSET[i];
  return {
    x: pt[0], y: pt[1], lx: lab[0] + tx2, ly: lab[1] + ty2, rank, r,
    numSize: r * 0.66, nameSize: r * 0.27,
    name: s.name || "", title: s.title || "",
    tx: lab[0] + ox, ty: lab[1] + oy,
  };
}));
const P3_EMBLEM = Array.from({ length: 10 }, (_, i) => {
  const a = (-90 + i * 36) * Math.PI / 180;
  const r = i % 2 ? 2.5 : 6;
  return [Math.cos(a) * r, Math.sin(a) * r];
}).map((p) => p.map((n) => n.toFixed(1)).join(",")).join(" ");

// 昵称过长时逐级缩小字号，避免撑爆档案头
const nameClass = computed(() => {
  const n = (data.value?.username || "").length;
  return n > 12 ? "name-xlong" : n > 8 ? "name-long" : "";
});

function fmtDate(v) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" });
}
function fmtDay(v) {
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "";
  return d.toLocaleDateString("zh-CN", { month: "numeric", day: "numeric" });
}
function back() {
  backFromPersona();
}
function openRecent(rec) {
  closePersona();
  openDetail(rec.id);
}

const TILES = [
  { key: "records", label: "累计记录", unit: "条" },
  { key: "likes_received", label: "获得点赞", unit: "次" },
  { key: "favorites_received", label: "被收藏", unit: "次" },
  { key: "comments_received", label: "收到留言", unit: "条" },
  { key: "quotes_received", label: "被转发", unit: "次" },
  { key: "likes_given", label: "送出点赞", unit: "次" },
  { key: "comments_given", label: "送出留言", unit: "条" },
  { key: "active_days", label: "活跃天数", unit: "天" },
];
</script>

<template>
  <section class="persona-page">
    <div class="persona-top">
      <button class="persona-back" type="button" title="返回" @click="back">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7"/></svg>
        <span>返回</span>
      </button>

      <div v-if="data" class="persona-id">
        <div class="persona-id-main">
          <div class="persona-eyebrow">PERSONAL FILE // 个人档案</div>
          <h1 class="persona-name" :class="nameClass">{{ data.username }}</h1>
          <div class="persona-meta">
            <span>加入于 {{ fmtDate(data.joined_at) }}</span>
            <i></i>
            <span>连续打卡 {{ summary.streak || 0 }} 天</span>
            <i></i>
            <span>累计 {{ summary.total_points || 0 }} 点</span>
          </div>
        </div>
      </div>

      <div v-else class="persona-loading">{{ p.loading ? "档案读取中…" : (p.msg || "档案不存在") }}</div>
    </div>

    <div v-if="data" class="persona-grid">
      <section class="panel persona-card persona-star-card">
        <div class="persona-card-head">
          <h2><span class="t-p5">人格指数</span><span class="t-p3">个人能力值</span></h2>
          <div class="persona-card-tools">
            <span class="persona-sub">SOCIAL STATS</span>
            <button class="persona-rules-btn" type="button" title="查看计分规则" @click="rulesOpen = true">
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M9.6 9.3a2.5 2.5 0 1 1 3.4 2.3c-.7.3-1 .9-1 1.6v.4" /><path d="M12 17h.01" /></svg>
              <span>计分规则</span>
            </button>
          </div>
        </div>

        <div class="persona-star">
          <svg viewBox="0 0 300 300" role="img" aria-label="五维人格星图">
            <defs>
              <!-- 星形底盘：原作里是一整块比金色星更深的灰色五角星 -->
              <linearGradient id="starFrame" gradientUnits="userSpaceOnUse" x1="40" y1="30" x2="250" y2="280">
                <stop offset="0" stop-color="#454545" />
                <stop offset="1" stop-color="#2c2c2c" />
              </linearGradient>
            </defs>
            <!-- P5（深色）：外框底盘 + 折面数据星 -->
            <g class="p5-chart">
              <polygon class="star-frame" :points="frameStar" />
              <circle
                v-for="(d, i) in frameDots" :key="'fd' + i"
                class="star-frame-dot" :cx="d[0]" :cy="d[1]" r="1.8"
              />
              <polygon
                v-for="(f, i) in starFacets" :key="'f' + i"
                class="star-facet" :fill="p5FacetColor(i)" :points="f"
              />
              <polygon
                v-for="(f, i) in goldFacets" :key="'cf' + i"
                class="star-facet" :fill="p5CoreColor(i)" :points="f"
              />
              <polygon class="star-outline" :points="dataStar" />
              <polygon class="star-outline" :points="goldStar" />
              <circle
                v-for="(d, i) in starDots" :key="'d' + i"
                class="star-dot" :cx="d[0]" :cy="d[1]" :r="starDotR"
              />
            </g>
            <!-- P3（浅色）：中心小星固定大小，五个半透明大圆圈为视觉主体，重叠处自然加深 -->
            <g class="p3-chart">
              <!-- 第一层：五个圆圈 -->
              <circle v-for="(c, i) in p3Dots" :key="'p3c' + i" class="p3-circle" :cx="c.x" :cy="c.y" :r="c.r" />
              <polygon class="p3-star" :points="p3Star" />
              <!-- 第二层：圈内的等级数字与能力名（压在圆圈之上，避免被相邻圆圈蒙住） -->
              <g v-for="(c, i) in p3Dots" :key="'p3n' + i" :transform="`rotate(-10 ${c.lx} ${c.ly})`">
                <text class="p3-num" :font-size="c.numSize" :x="c.lx - 8" :y="c.ly + 15" text-anchor="end">{{ c.rank }}</text>
                <text class="p3-name" :font-size="c.nameSize" :x="c.lx + 2" :y="c.ly + 14">{{ c.name }}</text>
              </g>
              <!-- 最上层：称号文字（无底框，深色字 + 白色描边，避免小黑圈配大黑条） -->
              <g v-for="(c, i) in p3Dots" :key="'p3t' + i" class="p3-tag" :transform="`rotate(-8 ${c.tx} ${c.ty})`">
                <polygon class="p3-tag-star" :points="P3_EMBLEM" :transform="`translate(${c.tx - 31} ${c.ty - 1})`" />
                <text class="p3-tag-txt" :x="c.tx - 19" :y="c.ty + 4">{{ c.title }}</text>
              </g>
            </g>
          </svg>
          <div v-for="s in stats" :key="s.key" class="star-tag" :class="'st-' + s.key">
            <span class="st-label">
              <b>{{ s.name }}</b>
              <em>{{ s.rank >= 5 ? "MAX" : "LV." + s.rank }}</em>
            </span>
            <small>{{ s.title }}</small>
          </div>
        </div>

        <div class="persona-stats">
          <div v-for="s in stats" :key="s.key" class="persona-stat">
            <div class="ps-head">
              <span class="ps-name">{{ s.name }}</span>
              <span class="ps-rank">LV.{{ s.rank }} · {{ s.title }}</span>
            </div>
            <div class="ps-bar"><i :style="{ width: (s.progress || 0) + '%' }"></i></div>
            <div class="ps-meta">
              <span>{{ s.value }} pt</span>
              <span v-if="s.next">距「{{ s.next_title }}」还差 {{ Math.max(0, s.next - s.value) }} 点</span>
              <span v-else>已满级</span>
            </div>
          </div>
        </div>
      </section>

      <div class="persona-side">
        <section class="panel persona-card persona-heat-card">
          <StudyHeatmap :days="data.days || {}" heading="学习打卡" flat />
        </section>

        <section class="panel persona-card">
          <div class="persona-card-head">
            <h2>档案摘要</h2>
            <span class="persona-sub">ARCHIVE</span>
          </div>
          <div class="persona-tiles">
            <div v-for="t in TILES" :key="t.key" class="persona-tile">
              <b>{{ summary[t.key] || 0 }}</b>
              <span>{{ t.label }}<small>{{ t.unit }}</small></span>
            </div>
          </div>
        </section>

        <section class="panel persona-card persona-recent-card">
          <div class="persona-card-head">
            <h2>最近记录</h2>
            <span class="persona-sub">RECENT</span>
          </div>
          <div v-if="!(data.recent || []).length" class="persona-empty">还没有发布过学习记录</div>
          <button
            v-for="rec in data.recent" :key="rec.id"
            class="persona-recent" type="button" @click="openRecent(rec)"
          >
            <span class="pr-day">{{ fmtDay(rec.created_at) }}</span>
            <span class="pr-text">{{ rec.preview || "（无文字内容）" }}</span>
          </button>
        </section>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="rulesOpen" class="modal-mask" @click.self="rulesOpen = false">
        <div class="modal-card persona-rules-card" role="dialog" aria-modal="true">
          <div class="modal-head">
            <h3>
              <svg class="modal-title-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M9.6 9.3a2.5 2.5 0 1 1 3.4 2.3c-.7.3-1 .9-1 1.6v.4" /><path d="M12 17h.01" /></svg>
              五维人格 · 计分规则
            </h3>
            <button class="modal-close" type="button" title="关闭" @click="rulesOpen = false">✕</button>
          </div>
          <p class="rules-lead">五个维度分别累计点数升级，互不影响。达到对应点数自动升到下一级。</p>

          <div class="rules-levels">
            <span v-for="(t, i) in PERSONA_THRESHOLDS" :key="t"><b>LV.{{ i + 1 }}</b>{{ t }} 点</span>
          </div>

          <div v-for="r in PERSONA_RULES" :key="r.key" class="rules-dim">
            <h4>{{ r.name }}<em>{{ (PERSONA_TITLES[r.key] || []).join(" → ") }}</em></h4>
            <ul>
              <li v-for="(it, i) in r.items" :key="i">{{ it }}</li>
            </ul>
          </div>

          <div class="rules-common">
            <h4>共同规则</h4>
            <ul>
              <li v-for="(it, i) in PERSONA_COMMON_RULES" :key="i">{{ it }}</li>
            </ul>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>
