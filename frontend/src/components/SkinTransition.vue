<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import { state, applyTheme, endSkinFx } from "../store.js";

/**
 * P3 ⇄ P5 全屏转场
 *   tide：P5(暗) → P3(亮)。潮水自页面顶端向下涌入，蓝色水体 + 白色浪花前缘；
 *         淹没整屏后切换主题，再向上褪去，P3 页面直接从水后露出。
 *   cape：P3(亮) → P5(暗)。纯黑披风自右上角斜掠至左下角覆盖整屏，
 *         再向原路掀开飞走，露出 P5 页面。
 *
 * 时长单位 ms；window.__skinFxSlow 可整体放慢，方便逐帧检查。
 */
const TIDE = { down: 880, hold: 240, up: 920 };
const CAPE = { cover: 700, hold: 220, lift: 820 };

const active = ref(false);
const mode = ref("tide");
const flipped = ref(false);

const tideBody = ref(null);
const capeSvg = ref(null);

let rafId = 0;
let lastTs = 0;
let elapsed = 0;
let swapped = false;
let running = false;
let capeCtx = null;
let dur = { ...TIDE };

/* ---------------- 缓动 ---------------- */
const easeSmooth = (p) => p * p * (3 - 2 * p);
const easeInOutCubic = (p) => (p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2);
const easeInOutQuart = (p) => (p < 0.5 ? 8 * p ** 4 : 1 - Math.pow(-2 * p + 2, 4) / 2);
const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);

/* ---------------- 浪花缎带 ----------------
 * 每条缎带的上下边各是一条叠加浪线，填充白色：上边是水体分界，下边是浪尖外缘。
 * 周期数取偶数 → 横向滚动 -50% 正好整数个周期，接缝不可见。
 * 波形刻意做成「峰尖谷圆」：正弦叠加后再做幂次整形，并叠一层慢速整体起伏，
 * 免得整条浪线像等幅正弦一样平整。
 */
const W = 1200;
const H = 220;
const SAMPLES = 640;

function waveY(base, amp, periods, phase, x) {
  const p = x / W;
  const u = p * Math.PI * 2 * periods;
  const v =
    Math.sin(u + phase) * 0.62 +
    Math.sin(u * 2 - phase * 1.25) * 0.24 +
    Math.sin(u * 3 + phase * 0.7) * 0.1 +
    Math.sin(u * 5 + phase * 2.1) * 0.045;
  // 软限幅：波峰压圆，避免出现尖角（tanh 只削顶、不制造拐点）
  const soft = Math.tanh(v * 1.2) / Math.tanh(1.2);
  // 低频整体起伏（每屏 2 个周期，滚动半屏仍是整数周期）
  const slow = Math.sin(p * Math.PI * 4 + phase * 0.8) * 0.22;
  return base + (soft + slow) * amp;
}

function buildRibbon(top, tAmp, tPeriods, tPhase, bot, bAmp, bPeriods, bPhase) {
  let d = "";
  const bottoms = [];
  for (let i = 0; i <= SAMPLES; i++) {
    const x = (W * i) / SAMPLES;
    const y = Math.max(1, Math.min(H, waveY(top, tAmp, tPeriods, tPhase, x)));
    d += (i ? " L" : "M") + x.toFixed(1) + "," + y.toFixed(1);
  }
  for (let i = SAMPLES; i >= 0; i--) {
    const x = (W * i) / SAMPLES;
    const y = Math.max(1, Math.min(H, waveY(bot, bAmp, bPeriods, bPhase, x)));
    bottoms.push([x, y]);
    d += " L" + x.toFixed(1) + "," + y.toFixed(1);
  }
  bottoms.reverse();
  return { d: d + " Z", bottoms };
}

// 浪形在转场开始时按视口宽度现算：SVG 横向铺 200% 视口，周期数按「每道浪约 200px」
// 换算成偶数，保证 -50% 横向滚动仍是整数个周期、接缝不可见。
// 这样桌面和手机上浪的尺度一致，不会在窄屏被压成一堆碎纹。
function buildWaves(vw) {
  const tile = vw * 2;
  const base = Math.max(6, Math.round(tile / 240 / 2) * 2);
  const A = buildRibbon(118, 14, base - 4, 0.4, 146, 14, base - 2, 2.1);
  const B = buildRibbon(138, 9, base - 2, 2.1, 156, 8, base, 4.2);
  const C = buildRibbon(150, 3, base, 4.2, 190, 10, base + 2, 1.1);
  const crest = "M" + C.bottoms.map((p) => `${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(" L");

  // 飞沫：取浪线局部最低点（浪尖）往外甩几颗水珠
  const spray = [];
  for (let i = 3; i < C.bottoms.length - 3; i++) {
    const b = C.bottoms;
    if (b[i][1] > b[i - 3][1] && b[i][1] > b[i + 3][1] && b[i][1] > 192) {
      spray.push({ x: +b[i][0].toFixed(1), y: +b[i][1].toFixed(1), r: 2.2 + (i % 6) * 0.8 });
    }
  }

  // 碎泡：沿浪线密排的小圆，把「一条干净的边」打碎成泡沫状
  const dots = [];
  const step = Math.max(1, Math.floor(C.bottoms.length / 110));
  for (let i = 0; i < C.bottoms.length; i += step) {
    const [x, y] = C.bottoms[i];
    const r = 1.4 + (((i * 37) % 100) / 100) * 3.6;
    dots.push({ x: +x.toFixed(1), y: +Math.min(212, y + r * 0.15).toFixed(1), r: +r.toFixed(1) });
  }

  return { A, B, C, crest, spray: spray.slice(0, 40), dots };
}

const wave = ref(buildWaves(1440));

/* 气泡：位置固定（只算一次），避免重挂时跳动 */
const bubbles = Array.from({ length: 13 }, (_, i) => {
  const r = (n) => (((Math.sin(i * 12.9898 + n * 78.233) * 43758.5453) % 1) + 1) % 1;
  const size = 3 + r(1) * 8;
  return {
    id: i,
    style: {
      left: (r(2) * 100).toFixed(2) + "%",
      width: size.toFixed(1) + "px",
      height: size.toFixed(1) + "px",
      animationDelay: (-r(3) * 7).toFixed(2) + "s",
      animationDuration: (5 + r(4) * 4).toFixed(2) + "s",
      opacity: (0.16 + r(5) * 0.3).toFixed(2),
    },
  };
});

/* ---------------- 披风（纯黑） ---------------- */
function capeInit() {
  const svg = capeSvg.value;
  if (!svg) return;
  const clip = svg.querySelector(".fx-cape__clip");
  const rim = svg.querySelector(".fx-cape__rim");
  const shade = svg.querySelector(".fx-cape__shade");
  const cast = svg.querySelector(".fx-cape__cast");
  const foldLayer = svg.querySelector(".fx-cape__folds");
  const gBlack = svg.querySelector("#fxCapeBlack");
  if (!clip || !rim || !shade || !foldLayer || !gBlack) {
    capeCtx = null;
    return;
  }

  const vw = window.innerWidth;
  const vh = window.innerHeight;
  gBlack.setAttribute("x1", "0");
  gBlack.setAttribute("y1", String(vh));
  gBlack.setAttribute("x2", String(vw));
  gBlack.setAttribute("y2", "0");

  foldLayer.innerHTML = "";
  const folds = [];
  const creases = [];
  const wrinkles = [];
  // 褶皱分三层：大褶皱（纺锤形光斑）→ 细折痕（抓光线）→ 碎褶（贴布边的小光斑）
  for (let i = 0; i < 13; i++) {
    const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
    p.setAttribute("class", "fx-cape__fold");
    p.setAttribute("fill-opacity", (0.13 + (i % 4) * 0.045).toFixed(3));
    foldLayer.appendChild(p);
    folds.push(p);
  }
  for (let i = 0; i < 8; i++) {
    const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
    p.setAttribute("class", "fx-cape__crease");
    p.setAttribute("stroke-opacity", (0.12 + (i % 3) * 0.06).toFixed(3));
    foldLayer.appendChild(p);
    creases.push(p);
  }
  for (let i = 0; i < 11; i++) {
    const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
    p.setAttribute("class", "fx-cape__wrinkle");
    p.setAttribute("fill-opacity", (0.14 + (i % 3) * 0.07).toFixed(3));
    foldLayer.appendChild(p);
    wrinkles.push(p);
  }
  capeCtx = { clip, rim, shade, cast, folds, creases, wrinkles };
}

function drawCape(k, amp, vw, vh, m) {
  const ctx = capeCtx;
  if (!ctx) return;
  const N = 44;
  const t = elapsed / 1000;
  const a1 = vh * 0.052 * amp;
  const a2 = vh * 0.024 * amp;
  const a3 = vh * 0.011 * amp;
  const pts = [];
  for (let i = 0; i <= N; i++) {
    const x = -m + ((vw + 2 * m) * i) / N;
    let y =
      x +
      k +
      Math.sin(x * 0.0042 + t * 2.1) * a1 +
      Math.sin(x * 0.0105 - t * 2.9 + 1.4) * a2 +
      Math.sin(x * 0.026 + t * 4.3 + 0.7) * a3;
    // 布料撕裂感：叠绝对值锯齿，让边缘出现尖角与破口
    y += Math.abs(Math.sin(x * 0.0075 + t * 1.6)) * vh * 0.026 * amp;
    y += Math.abs(Math.sin(x * 0.021 - t * 2.4)) * vh * 0.008 * amp;
    y = Math.max(-m, Math.min(vh + m, y));
    pts.push([x, y]);
  }

  let d = `M${(-m).toFixed(1)},${(-m).toFixed(1)} L${(vw + m).toFixed(1)},${(-m).toFixed(1)}`;
  for (let i = N; i >= 0; i--) d += ` L${pts[i][0].toFixed(1)},${pts[i][1].toFixed(1)}`;
  d += " Z";
  ctx.clip.setAttribute("d", d);

  let rim = `M${pts[0][0].toFixed(1)},${pts[0][1].toFixed(1)}`;
  for (let i = 1; i <= N; i++) rim += ` L${pts[i][0].toFixed(1)},${pts[i][1].toFixed(1)}`;
  ctx.rim.setAttribute("d", rim);
  ctx.shade.setAttribute("d", rim);
  if (ctx.cast) ctx.cast.setAttribute("d", rim);

  // 褶皱：自前缘向布料内侧散开（每道角度略不同，避免全部平行）
  // 锚点钳制在视口内：整屏遮住时前缘在屏幕下方，若不钳制褶皱会全部落到画外
  const len = Math.max(vw, vh) * 0.62;
  const clampX = (v) => Math.max(8, Math.min(vw - 8, v));
  const clampY = (v) => Math.max(24, Math.min(vh - 24, v));

  // 纺锤形光斑：两端收细、中间鼓起 —— 布褶的亮部就长这样
  const drawLens = (el, i, total, lenScale, spread, widthScale, wob) => {
    const idx = Math.round(((i + 0.5) / total) * N);
    const ax = clampX(pts[Math.min(N, Math.max(0, idx))][0]);
    const ay = clampY(pts[Math.min(N, Math.max(0, idx))][1]);
    const ang = -Math.PI / 4 + Math.sin(i * 1.9 + wob) * spread;
    const l = len * lenScale * (0.78 + 0.22 * Math.sin(i * 2.7 + wob));
    const w = len * 0.075 * widthScale * (0.65 + 0.45 * Math.sin(i * 1.3 + wob));
    const dx = Math.cos(ang);
    const dy = Math.sin(ang);
    const px = -dy;
    const py = dx;
    const ex = ax + dx * l;
    const ey = ay + dy * l;
    const mx = ax + dx * l * 0.42 + px * w + Math.sin(i * 2.1 + wob) * 26;
    const my = ay + dy * l * 0.42 + py * w + Math.cos(i * 1.7 + wob) * 26;
    const nx = ax + dx * l * 0.5 - px * w * 0.85;
    const ny = ay + dy * l * 0.5 - py * w * 0.85;
    el.setAttribute(
      "d",
      `M${ax.toFixed(1)},${ay.toFixed(1)} Q${mx.toFixed(1)},${my.toFixed(1)} ${ex.toFixed(1)},${ey.toFixed(1)} Q${nx.toFixed(1)},${ny.toFixed(1)} ${ax.toFixed(1)},${ay.toFixed(1)} Z`
    );
  };

  // 细折痕：一条细线，压在光斑旁边强调棱线
  const drawCrease = (el, i, total, lenScale, spread, wob) => {
    const idx = Math.round(((i + 0.5) / total) * N);
    const ax = clampX(pts[Math.min(N, Math.max(0, idx))][0]);
    const ay = clampY(pts[Math.min(N, Math.max(0, idx))][1]);
    const ang = -Math.PI / 4 + Math.sin(i * 1.9 + wob) * spread;
    const l = len * lenScale * (0.7 + 0.25 * Math.sin(i * 2.7 + wob));
    const dx = Math.cos(ang) * l;
    const dy = Math.sin(ang) * l;
    const cx = ax + dx * 0.34 + Math.sin(i * 2.1 + wob) * 40;
    const cy = ay + dy * 0.34 + Math.cos(i * 1.7 + wob) * 40;
    el.setAttribute(
      "d",
      `M${ax.toFixed(1)},${ay.toFixed(1)} Q${cx.toFixed(1)},${cy.toFixed(1)} ${(ax + dx).toFixed(1)},${(ay + dy).toFixed(1)}`
    );
  };

  ctx.folds.forEach((el, i) => drawLens(el, i, ctx.folds.length, 0.95, 0.26, 1, 0));
  ctx.creases.forEach((el, i) => drawCrease(el, i, ctx.creases.length, 0.8, 0.3, 1.7));
  ctx.wrinkles.forEach((el, i) => drawLens(el, i, ctx.wrinkles.length, 0.22, 0.5, 0.34, 3.1));
}

/* ---------------- 帧驱动 ---------------- */
function tideFrame() {
  const vh = window.innerHeight;
  const span = vh + 190; // 比浪花外溢再多一点，确保完全离屏
  let y;
  let drift = 0;
  if (elapsed < dur.down) {
    const p = clamp01(elapsed / dur.down);
    y = -span + span * easeSmooth(p);
    drift = Math.sin(elapsed / 160) * 10 * (1 - p);
  } else if (elapsed < dur.down + dur.hold) {
    y = 0;
  } else {
    const p = clamp01((elapsed - dur.down - dur.hold) / dur.up);
    y = -span * easeInOutQuart(p);
    drift = Math.sin(elapsed / 140) * 8 * (1 - p);
  }
  if (tideBody.value) {
    tideBody.value.style.transform = `translate3d(${drift.toFixed(2)}px, ${y.toFixed(2)}px, 0)`;
  }
}

function capeFrame() {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const m = Math.max(vw, vh) * 0.4;
  const kMin = -vw - m;
  const kMax = vh + m;
  let k;
  let amp;
  if (elapsed < dur.cover) {
    const p = clamp01(elapsed / dur.cover);
    k = kMin + (kMax - kMin) * Math.pow(p, 1.22);
    amp = 1;
  } else if (elapsed < dur.cover + dur.hold) {
    k = kMax;
    amp = 0.5;
  } else {
    const p = clamp01((elapsed - dur.cover - dur.hold) / dur.lift);
    k = kMax - (kMax - kMin) * easeInOutCubic(p);
    amp = 1.35;
  }
  drawCape(k, amp, vw, vh, m);
}

function frame(now) {
  if (!running) return;
  if (!lastTs) lastTs = now;
  elapsed += Math.min(80, now - lastTs);
  lastTs = now;

  const total = mode.value === "tide" ? dur.down + dur.hold + dur.up : dur.cover + dur.hold + dur.lift;

  // 全屏被遮住的瞬间切主题：页面在转场后面直接换掉，不露痕迹
  if (!swapped && elapsed >= (mode.value === "tide" ? dur.down : dur.cover)) {
    swapped = true;
    applyTheme(state.skinFx.next);
    if (mode.value === "cape") flipped.value = true;
  }

  if (mode.value === "tide") tideFrame();
  else capeFrame();

  if (elapsed >= total) {
    finish();
    return;
  }
  rafId = requestAnimationFrame(frame);
}

/* ---------------- 生命周期 ---------------- */
function stop() {
  running = false;
  if (rafId) cancelAnimationFrame(rafId);
  rafId = 0;
  lastTs = 0;
  capeCtx = null;
}

function finish() {
  stop();
  active.value = false;
  endSkinFx();
}

async function start() {
  stop();
  const slow = Number(window.__skinFxSlow || 1) || 1;
  mode.value = state.skinFx.mode;
  dur = mode.value === "tide"
    ? { down: TIDE.down * slow, hold: TIDE.hold * slow, up: TIDE.up * slow }
    : { cover: CAPE.cover * slow, hold: CAPE.hold * slow, lift: CAPE.lift * slow };
  elapsed = 0;
  swapped = false;
  flipped.value = false;
  // 浪形按当前视口重算，窄屏不会把浪压成一堆碎纹
  if (mode.value === "tide") wave.value = buildWaves(window.innerWidth);
  active.value = true;
  await nextTick();
  if (mode.value === "cape") capeInit();
  running = true;
  rafId = requestAnimationFrame(frame);
}

watch(
  () => state.skinFx.token,
  () => {
    if (state.skinFx.active) start();
  }
);

onBeforeUnmount(stop);
</script>

<template>
  <Teleport to="body">
    <div
      v-if="active"
      class="fx-stage"
      :class="{ 'is-settled': flipped }"
      :data-mode="mode"
      aria-hidden="true"
    >
      <!-- ============ 潮水：P5 → P3 ============ -->
      <div v-if="mode === 'tide'" class="fx-tide">
        <div ref="tideBody" class="fx-tide__body">
          <div class="fx-tide__fill"></div>
          <div class="fx-tide__depth"></div>
          <div class="fx-tide__light"></div>
          <div class="fx-tide__vignette"></div>
          <div class="fx-tide__bubbles">
            <i v-for="b in bubbles" :key="b.id" :style="b.style"></i>
          </div>
          <div class="fx-tide__crest">
            <div class="fx-wave-wrap fx-wave-wrap--a">
              <svg class="fx-wave" viewBox="0 0 1200 220" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="fxRibA" gradientUnits="userSpaceOnUse" x1="0" y1="118" x2="0" y2="165">
                    <stop offset="0" stop-color="#d6f2ff" stop-opacity="0" />
                    <stop offset="0.55" stop-color="#e6f8ff" stop-opacity="0.16" />
                    <stop offset="1" stop-color="#ffffff" stop-opacity="0.42" />
                  </linearGradient>
                </defs>
                <path :d="wave.A.d" fill="url(#fxRibA)" />
              </svg>
            </div>
            <div class="fx-wave-wrap fx-wave-wrap--b">
              <svg class="fx-wave" viewBox="0 0 1200 220" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="fxRibB" gradientUnits="userSpaceOnUse" x1="0" y1="138" x2="0" y2="170">
                    <stop offset="0" stop-color="#eefbff" stop-opacity="0" />
                    <stop offset="0.5" stop-color="#f4fcff" stop-opacity="0.22" />
                    <stop offset="1" stop-color="#ffffff" stop-opacity="0.66" />
                  </linearGradient>
                </defs>
                <path :d="wave.B.d" fill="url(#fxRibB)" />
              </svg>
            </div>
            <div class="fx-wave-wrap fx-wave-wrap--c">
              <svg class="fx-wave" viewBox="0 0 1200 220" preserveAspectRatio="none">
                <defs>
                  <linearGradient id="fxRibC" gradientUnits="userSpaceOnUse" x1="0" y1="150" x2="0" y2="200">
                    <stop offset="0" stop-color="#ffffff" stop-opacity="0.95" />
                    <stop offset="0.45" stop-color="#f6fdff" stop-opacity="1" />
                    <stop offset="0.78" stop-color="#eefaff" stop-opacity="0.97" />
                    <stop offset="1" stop-color="#dcefff" stop-opacity="0.62" />
                  </linearGradient>
                  <pattern id="fxFoamCells" width="18" height="18" patternUnits="userSpaceOnUse">
                    <circle cx="3" cy="4" r="2.4" fill="#8fbdd8" opacity="0.55" />
                    <circle cx="11" cy="2" r="1.2" fill="#a9cfe4" opacity="0.5" />
                    <circle cx="14" cy="9" r="2.9" fill="#7fb0cd" opacity="0.45" />
                    <circle cx="6" cy="13" r="1.6" fill="#a9cfe4" opacity="0.55" />
                    <circle cx="16" cy="16" r="1.1" fill="#8fbdd8" opacity="0.5" />
                    <circle cx="2" cy="10" r="0.9" fill="#bcdcee" opacity="0.6" />
                  </pattern>
                </defs>
                <path :d="wave.C.d" class="fx-foam-halo" fill="#ffffff" />
                <path :d="wave.C.d" fill="url(#fxRibC)" />
                <path :d="wave.C.d" fill="url(#fxFoamCells)" opacity="0.5" />
                <g class="fx-foam-dots">
                  <circle v-for="(d, i) in wave.dots" :key="'fd' + i" :cx="d.x" :cy="d.y" :r="d.r" fill="#ffffff" />
                </g>
                <path :d="wave.crest" fill="none" stroke="#ffffff" stroke-width="3" stroke-opacity="0.95" />
                <g class="fx-foam-spray">
                  <circle
                    v-for="(s, i) in wave.spray" :key="'sp' + i"
                    :cx="s.x" :cy="s.y + 2" :r="s.r"
                    fill="#ffffff" opacity="0.9"
                  />
                </g>
              </svg>
            </div>
            <div class="fx-tide__glow"></div>
          </div>
        </div>
      </div>

      <!-- ============ 披风（纯黑）：P3 → P5 ============ -->
      <div v-else class="fx-cape">
        <svg ref="capeSvg" class="fx-cape__svg">
          <defs>
            <linearGradient id="fxCapeBlack" gradientUnits="userSpaceOnUse" x1="0" y1="900" x2="1440" y2="0">
              <stop offset="0" stop-color="#010101" />
              <stop offset="0.26" stop-color="#141414" />
              <stop offset="0.44" stop-color="#050505" />
              <stop offset="0.62" stop-color="#1a1a1a" />
              <stop offset="0.8" stop-color="#000000" />
              <stop offset="1" stop-color="#101010" />
            </linearGradient>
            <pattern id="fxCapeWeave" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <line x1="0" y1="0" x2="0" y2="7" stroke="#fff" stroke-width="0.8" opacity="0.35" />
              <line x1="3.5" y1="0" x2="3.5" y2="7" stroke="#000" stroke-width="0.8" opacity="0.3" />
            </pattern>
            <linearGradient id="fxCapeSheenSweep" gradientUnits="userSpaceOnUse" x1="0" y1="900" x2="900" y2="0">
              <stop offset="0" stop-color="#ffffff" stop-opacity="0" />
              <stop offset="0.42" stop-color="#ffffff" stop-opacity="0" />
              <stop offset="0.5" stop-color="#ffffff" stop-opacity="0.3" />
              <stop offset="0.58" stop-color="#ffffff" stop-opacity="0" />
              <stop offset="1" stop-color="#ffffff" stop-opacity="0" />
            </linearGradient>
            <radialGradient id="fxCapeSheen" cx="0.78" cy="0.16" r="0.72">
              <stop offset="0" stop-color="#ffffff" stop-opacity="0.13" />
              <stop offset="0.5" stop-color="#ffffff" stop-opacity="0.04" />
              <stop offset="1" stop-color="#ffffff" stop-opacity="0" />
            </radialGradient>
            <clipPath id="fxCapeClip"><path class="fx-cape__clip" d="" /></clipPath>
          </defs>
          <g clip-path="url(#fxCapeClip)">
            <rect class="fx-cape__face" x="-3000" y="-3000" width="9000" height="9000" fill="url(#fxCapeBlack)" />
            <rect class="fx-cape__weave" x="-3000" y="-3000" width="9000" height="9000" fill="url(#fxCapeWeave)" />
            <rect class="fx-cape__sheen" x="-3000" y="-3000" width="9000" height="9000" fill="url(#fxCapeSheen)" />
            <g class="fx-cape__folds"></g>
            <path class="fx-cape__shade" d="" />
            <rect class="fx-cape__sweep" x="-3000" y="-3000" width="9000" height="9000" fill="url(#fxCapeSheenSweep)" />
            <path class="fx-cape__rim" d="" />
          </g>
          <path class="fx-cape__cast" d="" />
        </svg>
      </div>
    </div>
  </Teleport>
</template>

<style>
/* ================= 舞台 ================= */
.fx-stage {
  position: fixed;
  inset: 0;
  z-index: 5000;
  overflow: hidden;
  pointer-events: auto;
}

/* ================= 潮水：P5 → P3 ================= */
.fx-tide,
.fx-tide__body {
  position: absolute;
  inset: 0;
}

.fx-tide__body {
  will-change: transform;
  transform: translate3d(0, -100%, 0);
}

/* 水体底色（深水）：深浅锚定在水面（浪线）上，无论涌到哪一屏都是「越深越暗」 */
.fx-tide__fill {
  position: absolute;
  inset: 0;
  background: #053a70;
}

.fx-tide__depth {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1200px;
  background: linear-gradient(
    180deg,
    #04305f 0%,
    #06447f 30%,
    #08589f 58%,
    #0c6bb4 78%,
    #1780c6 92%,
    #2f95d6 100%
  );
}

/* 水下光池：几团大而柔的光斑缓慢漂移，替代规则网格 */
.fx-tide__light {
  position: absolute;
  inset: -12%;
  background:
    radial-gradient(38% 26% at 22% 30%, rgba(150, 220, 255, 0.22), transparent 70%),
    radial-gradient(30% 22% at 68% 54%, rgba(120, 205, 255, 0.16), transparent 72%),
    radial-gradient(26% 20% at 44% 76%, rgba(170, 230, 255, 0.14), transparent 70%),
    radial-gradient(34% 24% at 86% 18%, rgba(140, 215, 255, 0.18), transparent 70%);
  mix-blend-mode: screen;
  animation: fxLightDrift 18s ease-in-out infinite alternate;
}
@keyframes fxLightDrift {
  0% { transform: translate3d(-2%, 1%, 0) scale(1); }
  100% { transform: translate3d(3%, -2%, 0) scale(1.08); }
}

/* 水体暗角：越靠边越深，避免整块蓝看起来像纯色填充 */
.fx-tide__vignette {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 78% 62% at 48% 34%, transparent 38%, rgba(2, 22, 48, 0.42) 100%),
    linear-gradient(180deg, rgba(2, 20, 44, 0.35) 0%, transparent 26%);
  pointer-events: none;
}

.fx-tide__bubbles {
  position: absolute;
  inset: 0;
  overflow: hidden;
}
.fx-tide__bubbles i {
  position: absolute;
  bottom: -24px;
  border-radius: 50%;
  background: radial-gradient(
    circle at 50% 50%,
    rgba(255, 255, 255, 0.03) 38%,
    rgba(255, 255, 255, 0.5) 60%,
    rgba(255, 255, 255, 0.12) 76%,
    rgba(255, 255, 255, 0) 84%
  );
  animation-name: fxBubble;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  will-change: transform, opacity;
}
@keyframes fxBubble {
  0% { transform: translate3d(0, 0, 0) scale(0.6); opacity: 0; }
  18% { opacity: 1; }
  100% { transform: translate3d(26px, -96vh, 0) scale(1.2); opacity: 0; }
}

/* ---- 浪花带：贴在水体下缘 ---- */
.fx-tide__crest {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 340px;
  overflow: visible;
}

.fx-wave-wrap {
  position: absolute;
  left: 0;
  bottom: -70px;
  width: 100%;
  height: 300px;
}
.fx-wave-wrap--a { animation: fxBobA 6.4s ease-in-out infinite; }
.fx-wave-wrap--b { animation: fxBobB 5.1s ease-in-out infinite; }
.fx-wave-wrap--c { animation: fxBobC 4.2s ease-in-out infinite; }

@keyframes fxBobA { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(11px); } }
@keyframes fxBobB { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(9px); } }
@keyframes fxBobC { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(7px); } }

.fx-wave {
  display: block;
  width: 200%;
  height: 100%;
  animation: fxWaveScroll 24s linear infinite;
}
.fx-wave-wrap--b .fx-wave { animation-duration: 15s; animation-direction: reverse; }
.fx-wave-wrap--c .fx-wave { animation-duration: 10s; }

@keyframes fxWaveScroll {
  0% { transform: translate3d(0, 0, 0); }
  100% { transform: translate3d(-50%, 0, 0); }
}

/* 浪沫晕：泡沫下缘外扩的一层柔光，让水线不像剪纸边缘 */
.fx-foam-halo {
  filter: blur(6px);
  opacity: 0.22;
  transform: translateY(5px);
}

/* 浪尖冷光：紧贴水线的一层薄辉光 */
.fx-tide__glow {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -58px;
  height: 84px;
  background: linear-gradient(180deg, rgba(150, 222, 255, 0) 0%, rgba(198, 240, 255, 0.22) 55%, rgba(255, 255, 255, 0.36) 100%);
  filter: blur(9px);
  mix-blend-mode: screen;
  pointer-events: none;
}

/* ================= 披风（纯黑）：P3 → P5 ================= */
.fx-cape {
  position: absolute;
  inset: 0;
}
.fx-cape__svg {
  display: block;
  width: 100%;
  height: 100%;
}
.fx-cape__weave { opacity: 0.12; }
.fx-cape__sheen { opacity: 0.95; }

/* 覆盖完成的一瞬，一道柔光扫过布面，像布料落定 */
.fx-cape__sweep {
  opacity: 0;
  transform: translateX(-60%);
}
.fx-stage.is-settled .fx-cape__sweep {
  animation: fxCapeSweep 0.46s cubic-bezier(0.3, 0, 0.3, 1) both;
}
@keyframes fxCapeSweep {
  0% { opacity: 0; transform: translateX(-46%); }
  20% { opacity: 1; }
  100% { opacity: 0; transform: translateX(46%); }
}

/* 宽而柔的布面高光：纺锤形光斑，黑布上靠它看出起伏 */
.fx-cape__fold {
  fill: #cdcddb;
  stroke: none;
  filter: blur(16px);
}

/* 细折痕：抓光的一线 */
.fx-cape__crease {
  fill: none;
  stroke: #cfcfdd;
  stroke-width: 2.2;
  stroke-linecap: round;
  filter: blur(2.4px);
}

/* 碎褶：贴布边的短小光斑 */
.fx-cape__wrinkle {
  fill: #d2d2e0;
  stroke: none;
  filter: blur(7px);
}

/* 前缘投影：布料厚度 */
.fx-cape__shade {
  fill: none;
  stroke: rgba(0, 0, 0, 0.85);
  stroke-width: 36;
  stroke-linecap: round;
  opacity: 0.6;
  transform: translateY(-3px);
}

/* 投在页面上的影子：让披风「浮」起来 */
.fx-cape__cast {
  fill: none;
  stroke: rgba(0, 0, 0, 0.5);
  stroke-width: 64;
  stroke-linecap: round;
  filter: blur(22px);
  transform: translate(-14px, 26px);
  opacity: 0.75;
}

/* 前缘高光：P5 的米白描边 */
.fx-cape__rim {
  fill: none;
  stroke: #f7f3e6;
  stroke-width: 3.4;
  stroke-linecap: round;
  opacity: 0.92;
}

/* ================= 降级 ================= */
@media (prefers-reduced-motion: reduce) {
  .fx-wave,
  .fx-tide__caustics,
  .fx-tide__shafts,
  .fx-tide__bubbles i,
  .fx-wave-wrap {
    animation: none !important;
  }
}
</style>
