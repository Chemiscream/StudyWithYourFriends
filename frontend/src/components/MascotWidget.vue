<script setup>
// 看板娘「库莉姆」：右下角悬浮入口 + 手机式聊天面板，姿势随对话状态切换
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from "vue";
import { ai, send, clearHistory, ensureAiLoaded } from "../useAiChat.js";
import { state, askConfirm, showDialog } from "../store.js";
import { mdRender, esc } from "../utils/md.js";

const open = ref(false);
const input = ref("");
const chatBox = ref(null);
const pose = ref("idle");   // 待机默认用全身照：idle(站立) / read(看书) / play(逗猫) / listen(倾听) / wave / think / write / cheer / sleep
const bubble = ref("");       // 待机鼓励气泡文案（空=不显示）
const now = ref(new Date());
let poseTimer = null, bubbleTimer = null, bubbleHideTimer = null, idleTimer = null, clockTimer = null;
let greeted = false;          // 本次会话是否已做过招手问候

// 待机动作轮换组：没对话也没临时姿势时，在这几张之间慢速轮换，让她「自己有事做」
const IDLES = ["idle", "read", "play"];

// 素材版本号：更换立绘后递增，强制浏览器重新拉取，避免继续显示缓存的旧 PNG
const MASCOT_VER = "11";
const poseSrc = computed(() => "/mascot/" + pose.value + ".png?v=" + MASCOT_VER);
// 聊天面板头部用大头照特写，与右下角按钮的全身姿势区分
const headSrc = "/mascot/avatar.png?v=" + MASCOT_VER;
const clock = computed(() => {
  const p = (n) => String(n).padStart(2, "0");
  return `${p(now.value.getHours())}:${p(now.value.getMinutes())}`;
});

const CHEERS = [
  "今天也要加油哦～",
  "学累了就休息一下嘛",
  "坚持记录的人最厉害啦！",
  "有什么想聊的尽管来找我！",
  "要不要看看大家今天学了什么？",
];

function setPoseTemp(p, ms) {
  if (poseTimer) clearTimeout(poseTimer);
  pose.value = p;
  poseTimer = setTimeout(() => { pose.value = "idle"; }, ms);
}

function togglePanel() {
  if (open.value) { open.value = false; return; }
  if (!state.token) { showDialog("登录之后才能和库莉姆聊天哦～"); return; }
  open.value = true;
  bubble.value = "";
  ensureAiLoaded();
  if (!greeted) { greeted = true; setPoseTemp("wave", 3000); }
  nextTick(scrollBottom);
}

function scrollBottom() {
  const b = chatBox.value;
  if (b) b.scrollTop = b.scrollHeight;
}

async function doSend() {
  const q = input.value.trim();
  if (!q || ai.busy) return;
  input.value = "";
  if (poseTimer) clearTimeout(poseTimer);
  pose.value = "think";
  const r = await send(q);
  if (ai.streamError) setPoseTemp("sleep", 4000);
  else if (r) setPoseTemp("cheer", 2500);
  else pose.value = "idle";
}

async function doClear() {
  if (!(await askConfirm("确定清空你和库莉姆的全部对话记录？"))) return;
  await clearHistory();
}

// 流式开始后从「思考」切换为「书写」
watch(() => ai.streaming, (v) => {
  if (v && pose.value === "think") pose.value = "write";
  nextTick(scrollBottom);
});
// 用户在输入框打字时切换为「倾听」，清空后回待机
watch(input, (v) => {
  if (!open.value || ai.busy) return;
  const t = v.trim();
  if (t && IDLES.includes(pose.value)) pose.value = "listen";
  else if (!t && pose.value === "listen") pose.value = "idle";
});
// 待机动作慢速轮换：仅当处于待机组（非问候/思考/倾听等临时姿势）时推进
function scheduleIdle() {
  idleTimer = setTimeout(() => {
    if (!ai.busy && IDLES.includes(pose.value)) {
      const rest = IDLES.filter((p) => p !== pose.value);
      pose.value = rest[Math.floor(Math.random() * rest.length)];
    }
    scheduleIdle();
  }, 22000 + Math.random() * 16000);
}
watch(() => ai.history.length, () => nextTick(scrollBottom));

// 待机鼓励气泡：面板关闭时每 40~80 秒冒一句，8 秒后自动消失
function scheduleBubble() {
  bubbleTimer = setTimeout(() => {
    if (!open.value && !document.hidden) {
      bubble.value = CHEERS[Math.floor(Math.random() * CHEERS.length)];
      if (bubbleHideTimer) clearTimeout(bubbleHideTimer);
      bubbleHideTimer = setTimeout(() => { bubble.value = ""; }, 8000);
    }
    scheduleBubble();
  }, 40000 + Math.random() * 40000);
}

onMounted(() => {
  scheduleBubble();
  scheduleIdle();
  clockTimer = setInterval(() => { now.value = new Date(); }, 15000);
  // 预加载姿势图，避免状态切换时闪烁
  ["idle", "wave", "think", "write", "cheer", "sleep", "avatar", "read", "play", "listen"].forEach((p) => {
    const img = new Image();
    img.src = "/mascot/" + p + ".png?v=" + MASCOT_VER;
  });
});
onUnmounted(() => {
  clearTimeout(poseTimer);
  clearTimeout(bubbleTimer);
  clearTimeout(bubbleHideTimer);
  clearTimeout(idleTimer);
  clearInterval(clockTimer);
});
</script>

<template>
  <div class="mascot-wrap" :class="{ 'is-open': open }">
    <transition name="mascot-pop">
      <div v-if="open" class="mascot-panel">
        <div class="mp-status">
          <span>{{ clock }}</span>
          <span class="mp-sys"><i>▮▮▮</i> 5G <i>▮</i> 100%</span>
        </div>
        <div class="mascot-head">
          <span class="mascot-head-ava"><img class="mascot-head-img" :src="headSrc" alt="库莉姆"></span>
          <div class="mascot-title">
            <span class="mp-eyebrow">KURIMU // AI LINK</span>
            <div class="mascot-name">库莉姆</div>
            <div class="mascot-sub"><i class="mp-dot"></i>ONLINE · 看板娘</div>
          </div>
          <button class="mascot-x" type="button" title="清空对话" @click="doClear">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V5h6v2M7 7l1 13h8l1-13M10 11v5M14 11v5"/></svg>
          </button>
          <button class="mascot-x" type="button" title="关闭" @click="open = false">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>
          </button>
        </div>
        <div ref="chatBox" class="mascot-chat">
          <div class="mp-day">TODAY / 学习档案聊天</div>
          <div v-if="!ai.history.length && !ai.streaming && !ai.streamError" class="mascot-empty">
            你好呀，我是库莉姆～<br>跟我聊聊大家的学习情况吧！
          </div>
          <div v-for="(m, i) in ai.history" :key="i" class="chat-msg" :class="m.role === 'user' ? 'user' : 'ai'">
            <span v-if="m.role !== 'user'" class="chat-ava"><img :src="headSrc" alt=""></span>
            <div class="chat-body">
              <span class="chat-who">{{ m.role === "user" ? "YOU" : "KURIMU" }}</span>
              <div class="chat-bubble" v-html="m.role === 'user' ? esc(m.content) : mdRender(m.content)"></div>
            </div>
          </div>
          <div v-if="ai.streaming" class="chat-msg ai">
            <span class="chat-ava"><img :src="headSrc" alt=""></span>
            <div class="chat-body">
              <span class="chat-who">KURIMU</span>
              <div class="chat-bubble" v-html="mdRender(ai.streaming)"></div>
            </div>
          </div>
          <div v-if="ai.streamError" class="chat-msg ai">
            <span class="chat-ava"><img :src="headSrc" alt=""></span>
            <div class="chat-body">
              <span class="chat-who">KURIMU</span>
              <div class="chat-bubble"><svg class="chat-warn" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3.6L21.4 20H2.6z"/><path d="M12 9.4v5"/><path d="M12 17.4h.01"/></svg>{{ ai.streamError }}</div>
            </div>
          </div>
        </div>
        <div class="chat-form mascot-form">
          <input v-model="input" placeholder="和库莉姆聊聊…" maxlength="2000" @keydown.enter="doSend">
          <button class="mp-send" type="button" :disabled="ai.busy" title="发送" @click="doSend">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 3L10 14"/><path d="M21 3l-7 18-4-7-7-4z"/></svg>
          </button>
        </div>
        <div class="chat-status mascot-status" :style="ai.statusErr ? 'color:var(--danger)' : ''">{{ ai.status }}</div>
      </div>
    </transition>
    <transition name="mascot-bub">
      <div v-if="bubble && !open" class="mascot-bubble" @click="togglePanel">{{ bubble }}</div>
    </transition>
    <button class="mascot-btn" type="button" title="库莉姆" @click="togglePanel">
      <img :src="poseSrc" alt="库莉姆">
    </button>
  </div>
</template>

<style scoped>
.mascot-wrap {
  position: fixed;
  right: 20px;
  bottom: 14px;
  z-index: 90;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.mascot-btn {
  width: 118px;
  height: 118px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  animation: mascot-bob 3.6s ease-in-out infinite;
  transition: transform .15s ease;
}
.mascot-btn:hover { transform: scale(1.04); }
.mascot-btn img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  filter: drop-shadow(0 12px 18px rgba(14, 36, 56, .22));
}
@keyframes mascot-bob {
  0%, 100% { translate: 0 0; }
  50% { translate: 0 -6px; }
}
.mascot-bubble {
  max-width: 230px;
  background: #fff;
  border: 1px solid var(--primary);
  clip-path: polygon(0 0, 100% 0, 100% calc(100% - 10px), calc(100% - 10px) 100%, 0 100%);
  padding: 9px 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink);
  box-shadow: 4px 4px 0 color-mix(in srgb, var(--primary) 22%, transparent);
  cursor: pointer;
  user-select: none;
}

/* 手机式聊天面板 —— P3 浅色 */
.mascot-panel {
  width: 372px;
  max-width: calc(100vw - 24px);
  height: 560px;
  max-height: calc(100vh - 150px);
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 2px solid #0e2438;
  clip-path: polygon(0 0, calc(100% - 18px) 0, 100% 18px, 100% 100%, 18px 100%, 0 calc(100% - 18px));
  box-shadow: 8px 8px 0 rgba(0, 153, 192, .55), 0 30px 70px rgba(14, 36, 56, .22);
  overflow: hidden;
}
.mp-status {
  flex: none;
  height: 26px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: var(--mono);
  font-size: 8px;
  letter-spacing: .08em;
  color: #fff;
  background: var(--primary);
}
.mp-sys i { font-style: normal; }
.mascot-head {
  flex: none;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 13px;
  border-bottom: 1px solid var(--line);
  background: var(--panel-soft);
}
.mascot-head-ava {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 50%;
  border: 1px solid var(--primary);
  background: #fff;
}
/* 头像放大取景：避开原图边缘的碎星和书本，只保留脸与头发 */
.mascot-head-img {
  display: block;
  position: relative;
  left: -17%;
  top: -13%;
  width: 134%;
  height: 134%;
  object-fit: cover;
}
.mascot-title { flex: 1; min-width: 0; }
.mp-eyebrow {
  display: inline-block;
  padding: 1px 5px;
  font-family: var(--mono);
  font-size: 7px;
  letter-spacing: .14em;
  color: #fff;
  background: var(--primary);
  clip-path: polygon(0 0, 100% 0, calc(100% - 5px) 100%, 0 100%);
}
.mascot-name { margin-top: 3px; font-weight: 700; font-size: 14px; color: var(--ink); line-height: 1.2; }
.mascot-sub { display: flex; align-items: center; gap: 5px; margin-top: 2px; font-family: var(--mono); font-size: 7.5px; letter-spacing: .08em; color: var(--muted); }
.mp-dot { width: 5px; height: 5px; border-radius: 50%; background: #0aa06a; display: inline-block; }
.mascot-x {
  width: 27px;
  height: 27px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--muted);
  padding: 0;
  flex: none;
}
.mascot-x svg { width: 13px; height: 13px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
.mascot-x:hover { color: var(--primary); border-color: var(--primary); }
.mascot-chat {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 11px 13px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background:
    repeating-linear-gradient(180deg, transparent 0 23px, rgba(0, 153, 192, .035) 23px 24px),
    #fff;
}
.mp-day {
  align-self: center;
  font-family: var(--mono);
  font-size: 7.5px;
  letter-spacing: .14em;
  color: var(--muted);
  padding: 2px 8px;
  border: 1px solid var(--line);
  background: var(--panel-soft);
}
.mascot-empty {
  color: var(--muted);
  font-size: 12px;
  text-align: center;
  padding: 26px 12px;
  line-height: 1.9;
}
.chat-msg { display: flex; align-items: flex-end; gap: 8px; min-width: 0; }
.chat-msg.user { justify-content: flex-end; }
.chat-ava { display: none; }
.chat-body { min-width: 0; max-width: 88%; display: flex; flex-direction: column; gap: 3px; align-items: flex-start; }
.chat-msg.user .chat-body { align-items: flex-end; }
.chat-who {
  font-family: var(--mono);
  font-size: 7px;
  letter-spacing: .14em;
  color: var(--primary-dark);
  padding: 0 2px;
}
.chat-msg.user .chat-who { color: var(--muted); }
.chat-bubble {
  max-width: 100%;
  padding: 8px 11px;
  font-size: 12.5px;
  line-height: 1.72;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  border: 1px solid var(--line);
  background: var(--panel-soft);
  color: var(--ink);
  clip-path: polygon(0 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%);
  box-shadow: 2px 2px 0 rgba(0, 153, 192, .12);
}
.chat-msg.user .chat-bubble {
  background: linear-gradient(110deg, #18b6d6, var(--primary));
  border-color: transparent;
  color: #fff;
  clip-path: polygon(0 0, 100% 0, 100% 100%, 8px 100%, 0 calc(100% - 8px));
  box-shadow: 2px 2px 0 rgba(0, 120, 160, .28);
}
.mascot-form {
  flex: none;
  margin-top: 0;
  padding: 9px 12px 4px;
  gap: 7px;
  border-top: 1px solid var(--line);
  background: var(--panel-soft);
}
.mascot-form input {
  height: 38px;
  min-height: 38px;
  font-size: 12px;
  border-radius: 0;
}
.mp-send {
  width: 42px;
  height: 38px;
  min-height: 38px;
  display: grid;
  place-items: center;
  flex: none;
  border: 1px solid var(--primary);
  background: var(--primary);
  color: #fff;
  clip-path: polygon(6px 0, 100% 0, calc(100% - 6px) 100%, 0 100%);
}
.mp-send:hover { background: var(--primary-dark); }
.mp-send:disabled { opacity: .55; }
.mp-send svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.mascot-status {
  flex: none;
  margin-top: 0;
  padding: 0 13px 9px;
  font-family: var(--mono);
  font-size: 8.5px;
  color: var(--muted);
  letter-spacing: .04em;
  background: var(--panel-soft);
}
.mascot-pop-enter-active, .mascot-pop-leave-active { transition: all .2s ease; }
.mascot-pop-enter-from, .mascot-pop-leave-to { opacity: 0; transform: translateY(14px) scale(.98); }
.mascot-bub-enter-active, .mascot-bub-leave-active { transition: all .25s ease; }
.mascot-bub-enter-from, .mascot-bub-leave-to { opacity: 0; transform: translateY(6px); }

/* 手机式聊天面板 —— P5 深色（黑底白描边 + 红色贴纸 + 漫画气泡） */
html[data-theme="dark"] .mascot-btn img {
  filter: drop-shadow(5px 5px 0 rgba(230, 0, 18, .9)) drop-shadow(0 12px 18px rgba(0, 0, 0, .6));
}
html[data-theme="dark"] .mascot-bubble {
  background: #f7f3e6;
  border: 2px solid #111;
  color: #111;
  font-weight: 700;
  box-shadow: 5px 5px 0 #e60012;
  clip-path: none;
  transform: rotate(-1.5deg);
}
html[data-theme="dark"] .mascot-panel {
  background:
    radial-gradient(rgba(247, 243, 230, .07) 1px, transparent 1.3px) 0 0 / 9px 9px,
    #080808;
  border: 3px solid #f7f3e6;
  clip-path: none;
  box-shadow: 10px 10px 0 #e60012, 0 30px 70px rgba(0, 0, 0, .7);
  transform: rotate(-1deg);
}
html[data-theme="dark"] .mp-status { display: none; }
html[data-theme="dark"] .mascot-head { background: #080808; border-bottom: 3px solid #e60012; }
html[data-theme="dark"] .mascot-head-ava {
  width: 44px; height: 46px;
  border: 3px solid #f7f3e6;
  border-radius: 0;
  background: #111;
  box-shadow: 3px 3px 0 #e60012;
  transform: rotate(-4deg);
}
html[data-theme="dark"] .mascot-head-img {
  left: -25%; top: -16%;
  width: 150%; height: 150%;
  filter:
    drop-shadow(1px 0 0 #111) drop-shadow(-1px 0 0 #111)
    drop-shadow(0 1px 0 #111) drop-shadow(0 -1px 0 #111);
}
html[data-theme="dark"] .mp-eyebrow {
  background: #f7f3e6; color: #111; border: 2px solid #111;
  box-shadow: 2px 2px 0 #e60012; transform: skewX(-6deg); font-weight: 700;
}
html[data-theme="dark"] .mascot-name {
  color: #fff; font-family: var(--font-display); font-style: italic; font-size: 17px;
  transform: skewX(-4deg); text-shadow: 2px 2px 0 #e60012;
}
html[data-theme="dark"] .mascot-sub { color: #f7f3e6; }
html[data-theme="dark"] .mp-dot { background: #fff; box-shadow: 0 0 0 2px #e60012; }
html[data-theme="dark"] .mascot-x {
  background: #0a0a0a; border: 2px solid #f7f3e6; color: #fff;
  box-shadow: 2px 2px 0 #e60012;
}
html[data-theme="dark"] .mascot-x:hover { background: #f7f3e6; color: #111; border-color: #111; }
html[data-theme="dark"] .mascot-chat {
  gap: 18px;
  padding: 18px 12px 22px;
  background:
    linear-gradient(102deg, transparent 0 16%, #0a0a0a 16% 17.6%, transparent 17.6%),
    linear-gradient(78deg, transparent 0 82%, #0a0a0a 82% 83.6%, transparent 83.6%),
    radial-gradient(rgba(0, 0, 0, .28) 1.1px, transparent 1.4px) 0 0 / 8px 8px,
    #e60012;
}
html[data-theme="dark"] .mp-day {
  color: #111; background: #f7f3e6; border: 2px solid #111;
  box-shadow: 3px 3px 0 #111; transform: rotate(-2.5deg); font-weight: 700;
  padding: 3px 9px;
}
html[data-theme="dark"] .mascot-empty { color: #fff; font-weight: 700; text-shadow: 2px 2px 0 #111; }
/* P5 游戏内消息：左侧斜切头像框 + 不规则分镜气泡 */
html[data-theme="dark"] .chat-who { display: none; }
html[data-theme="dark"] .chat-ava {
  display: block;
  position: relative;
  width: 42px; height: 46px;
  flex: none;
  overflow: hidden;
  background: #ffd400;
  border: 3px solid #111;
  box-shadow: 3px 3px 0 #f7f3e6;
  transform: rotate(-4deg);
}
/* 放大并上移取景：裁掉原图边缘的碎星与书本残留，再用 1px 黑描边盖住抠图毛边 */
html[data-theme="dark"] .chat-ava img {
  position: absolute;
  left: -28%; top: -14%;
  width: 152%; height: 152%;
  object-fit: cover;
  filter:
    drop-shadow(1px 0 0 #111) drop-shadow(-1px 0 0 #111)
    drop-shadow(0 1px 0 #111) drop-shadow(0 -1px 0 #111);
}
html[data-theme="dark"] .chat-msg:nth-of-type(3n) .chat-ava { background: #ff9ec4; transform: rotate(4deg); }
html[data-theme="dark"] .chat-msg:nth-of-type(3n+1) .chat-ava { background: #8ecbff; transform: rotate(-3deg); }
html[data-theme="dark"] .mascot-chat .chat-msg .chat-bubble {
  position: relative;
  isolation: isolate;
  max-width: 100%;
  background: transparent;
  color: #f7f3e6;
  border: 0;
  padding: 13px 16px;
  font-weight: 600;
  line-height: 1.7;
  clip-path: none;
  filter: none;
  transform: rotate(-0.4deg);
}
/* 斜切分镜只画在背景层，文字层不参与裁剪，避免笔画被斜线切掉 */
html[data-theme="dark"] .mascot-chat .chat-msg .chat-bubble::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  background: #0b0b0b;
  clip-path: polygon(0 10%, 12% 0, 100% 4%, 97% 100%, 10% 96%, 5% 100%, 3% 84%, 0 76%);
  filter: drop-shadow(3px 0 0 #f7f3e6) drop-shadow(-3px 0 0 #f7f3e6) drop-shadow(0 3px 0 #f7f3e6) drop-shadow(0 -3px 0 #f7f3e6);
}
html[data-theme="dark"] .mascot-chat .chat-msg.user .chat-bubble {
  background: transparent;
  color: #111;
  padding: 13px 16px;
  font-weight: 700;
  clip-path: none;
  filter: none;
  transform: rotate(0.5deg);
}
html[data-theme="dark"] .mascot-chat .chat-msg.user .chat-bubble::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  background: #f7f3e6;
  clip-path: polygon(3% 6%, 86% 0, 100% 14%, 100% 72%, 96% 80%, 98% 100%, 8% 94%, 4% 72%, 0 62%);
  filter: drop-shadow(3px 0 0 #111) drop-shadow(-3px 0 0 #111) drop-shadow(0 3px 0 #111) drop-shadow(0 -3px 0 #111);
}
html[data-theme="dark"] .mascot-form { background: #080808; border-top: 3px solid #f7f3e6; }
html[data-theme="dark"] .mascot-form input { background: #111; border: 2px solid #f7f3e6; color: #fff; }
html[data-theme="dark"] .mp-send {
  background: #e60012; border: 2px solid #fff; box-shadow: 3px 3px 0 #000;
  clip-path: none; transform: skewX(-4deg);
}
html[data-theme="dark"] .mp-send:hover { background: #ff2b3a; }
html[data-theme="dark"] .mascot-status { background: #080808; color: #f7f3e6; }
@media (max-width: 640px) {
  html[data-theme="dark"] .mascot-panel { transform: none; box-shadow: 7px 7px 0 #e60012; }
}
@media (max-width: 640px) {
  .mascot-wrap { right: 12px; }
  .mascot-btn { width: 86px; height: 86px; }
  /* 面板打开时收起悬浮按钮，把高度让给聊天窗口 */
  .mascot-wrap.is-open .mascot-btn { display: none; }
  /* 用动态视口高度计算，避免地址栏收缩可视区域后聊天面板顶部被截断；
     112px = 底部 92px 偏移 + 顶部 20px 安全间距 */
  .mascot-panel {
    height: min(560px, calc(100vh - 200px));
    max-height: calc(100vh - 200px);
    height: min(560px, calc(100dvh - 112px - env(safe-area-inset-bottom)));
    max-height: calc(100dvh - 112px - env(safe-area-inset-bottom));
  }
}
</style>
