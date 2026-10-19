<script setup>
// 看板娘「库莉姆」：右下角悬浮入口 + 聊天面板，姿势随对话状态切换
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from "vue";
import { ai, send, clearHistory, ensureAiLoaded } from "../useAiChat.js";
import { state, askConfirm, showDialog } from "../store.js";
import { mdRender, esc } from "../utils/md.js";

const open = ref(false);
const input = ref("");
const chatBox = ref(null);
const pose = ref("avatar");   // avatar(待机特写) / idle(站立) / read(看书) / play(逗猫) / listen(倾听) / wave / think / write / cheer / sleep
const bubble = ref("");       // 待机鼓励气泡文案（空=不显示）
let poseTimer = null, bubbleTimer = null, bubbleHideTimer = null, idleTimer = null;
let greeted = false;          // 本次会话是否已做过招手问候

// 待机动作轮换组：没对话也没临时姿势时，在这几张之间慢速轮换，让她「自己有事做」
const IDLES = ["avatar", "idle", "read", "play"];

const poseSrc = computed(() => "/mascot/" + pose.value + ".png");

const CHEERS = [
  "今天也要加油哦～",
  "学累了就休息一下嘛",
  "坚持记录的人最厉害啦！",
  "有什么想聊的尽管来找我！",
  "要不要看看大家今天学了什么？",
  "喵～（黑猫打了个滚）",
];

function setPoseTemp(p, ms) {
  if (poseTimer) clearTimeout(poseTimer);
  pose.value = p;
  poseTimer = setTimeout(() => { pose.value = "avatar"; }, ms);
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
  else pose.value = "avatar";
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
  else if (!t && pose.value === "listen") pose.value = "avatar";
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
  // 预加载姿势图，避免状态切换时闪烁
  ["idle", "wave", "think", "write", "cheer", "sleep", "avatar", "read", "play", "listen"].forEach((p) => {
    const img = new Image();
    img.src = "/mascot/" + p + ".png";
  });
});
onUnmounted(() => {
  clearTimeout(poseTimer);
  clearTimeout(bubbleTimer);
  clearTimeout(bubbleHideTimer);
  clearTimeout(idleTimer);
});
</script>

<template>
  <div class="mascot-wrap">
    <transition name="mascot-pop">
      <div v-if="open" class="mascot-panel">
        <div class="mascot-head">
          <img class="mascot-head-img" :src="poseSrc" alt="库莉姆">
          <div class="mascot-title">
            <div class="mascot-name">库莉姆</div>
            <div class="mascot-sub">网站看板娘 · 爱学习也爱聊天</div>
          </div>
          <button class="mascot-x" type="button" title="清空对话" @click="doClear">🗑</button>
          <button class="mascot-x" type="button" title="关闭" @click="open = false">✕</button>
        </div>
        <div ref="chatBox" class="mascot-chat">
          <div v-if="!ai.history.length && !ai.streaming && !ai.streamError" class="mascot-empty">
            你好呀，我是库莉姆～<br>跟我聊聊大家的学习情况吧！
          </div>
          <div v-for="(m, i) in ai.history" :key="i" class="chat-msg" :class="m.role === 'user' ? 'user' : 'ai'">
            <div class="chat-bubble" v-html="m.role === 'user' ? esc(m.content) : mdRender(m.content)"></div>
          </div>
          <div v-if="ai.streaming" class="chat-msg ai">
            <div class="chat-bubble" v-html="mdRender(ai.streaming)"></div>
          </div>
          <div v-if="ai.streamError" class="chat-msg ai">
            <div class="chat-bubble">⚠️ {{ ai.streamError }}</div>
          </div>
        </div>
        <div class="chat-form mascot-form">
          <input v-model="input" placeholder="和库莉姆聊聊…" maxlength="2000" @keydown.enter="doSend">
          <button class="btn primary" type="button" :disabled="ai.busy" @click="doSend">发送</button>
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
  right: 18px;
  bottom: 18px;
  z-index: 90;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.mascot-btn {
  width: 100px;
  height: 100px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  animation: mascot-bob 3.2s ease-in-out infinite;
  transition: transform .15s ease;
}
.mascot-btn:hover { transform: scale(1.05); }
.mascot-btn img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  filter: drop-shadow(0 10px 16px rgba(26, 36, 53, .30));
}
@keyframes mascot-bob {
  0%, 100% { translate: 0 0; }
  50% { translate: 0 -6px; }
}
.mascot-bubble {
  max-width: 220px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 14px;
  border-bottom-right-radius: 4px;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--ink);
  box-shadow: var(--shadow);
  cursor: pointer;
  user-select: none;
}
.mascot-panel {
  width: 380px;
  max-width: calc(100vw - 24px);
  height: 520px;
  max-height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 14px;
  box-shadow: var(--shadow);
  overflow: hidden;
}
.mascot-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
.mascot-head-img {
  width: 44px;
  height: 44px;
  object-fit: contain;
  flex-shrink: 0;
}
.mascot-title { flex: 1; min-width: 0; }
.mascot-name { font-weight: 700; font-size: 15px; color: var(--ink); }
.mascot-sub { font-size: 12px; color: var(--muted); }
.mascot-x {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: var(--muted);
  padding: 6px 8px;
  border-radius: 8px;
  line-height: 1;
}
.mascot-x:hover { color: var(--ink); background: rgba(74, 144, 217, .12); }
.mascot-chat {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.mascot-empty {
  color: var(--muted);
  font-size: 13px;
  text-align: center;
  padding: 28px 12px;
  line-height: 1.8;
}
.mascot-form { margin-top: 0; padding: 10px 12px 4px; }
.mascot-status { margin-top: 0; padding: 0 14px 10px; }
.mascot-pop-enter-active, .mascot-pop-leave-active { transition: all .18s ease; }
.mascot-pop-enter-from, .mascot-pop-leave-to { opacity: 0; transform: translateY(12px) scale(.97); }
.mascot-bub-enter-active, .mascot-bub-leave-active { transition: all .25s ease; }
.mascot-bub-enter-from, .mascot-bub-leave-to { opacity: 0; transform: translateY(6px); }
@media (max-width: 640px) {
  .mascot-wrap { right: 12px; bottom: 12px; }
  .mascot-btn { width: 84px; height: 84px; }
  .mascot-panel { height: min(520px, calc(100vh - 120px)); }
}
</style>
