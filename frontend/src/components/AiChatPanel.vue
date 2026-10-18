<script setup>
import { ref, computed, watch, nextTick, onUnmounted } from "vue";
import { ai, send, clearHistory, setProvider, setLocalModel, saveAiCfgFields, loadModels, ensureAiLoaded } from "../useAiChat.js";
import { askConfirm } from "../store.js";
import { mdRender, esc } from "../utils/md.js";

const collapsed = ref(localStorage.getItem("studylog_collapse_ai") === "1");
const input = ref("");
const chatBox = ref(null);

const isApi = computed(() => ai.cfg.provider === "api");
const autoLabel = computed(() =>
  ai.models.length ? `自动（${ai.models[0]}）` : "自动");
const cfgHint = computed(() => isApi.value
  ? "支持任意 OpenAI 兼容 API（如 DeepSeek、GPT）。设置只保存在本浏览器；Key 仅在提问时由服务器转发，不会被存储。"
  : "默认使用服务器上的 AI 模型（LM Studio，无需 Key）。模型选「自动」时使用第一个可用模型；也可在下拉框指定。");

const baseUrl = ref(ai.cfg.base_url);
const apiKey = ref(ai.cfg.api_key);
const apiModel = ref(ai.cfg.api_model);

function toggle() {
  collapsed.value = !collapsed.value;
  localStorage.setItem("studylog_collapse_ai", collapsed.value ? "1" : "0");
  if (!collapsed.value) nextTick(scrollBottom);
}

function scrollBottom() {
  const box = chatBox.value;
  if (box) box.scrollTop = box.scrollHeight;
}

async function doSend() {
  const q = input.value.trim();
  if (!q || ai.busy) return;
  input.value = "";
  await send(q);
}

async function doClear(e) {
  e.stopPropagation();
  if (!(await askConfirm("确定清空你的全部 AI 对话记录？"))) return;
  await clearHistory();
}

function saveCfg() {
  saveAiCfgFields(baseUrl.value, apiKey.value, apiModel.value);
}

watch(() => ai.history.length, () => nextTick(scrollBottom));
watch(() => ai.streaming, () => nextTick(scrollBottom));
// 登录态出现时加载历史/模型；切换到本地模式时刷新模型列表
watch(() => ai.cfg.provider, (p) => { if (p === "local") loadModels(); });
ensureAiLoaded();
onUnmounted(() => {});
</script>

<template>
  <section class="panel" :class="{ collapsed }">
    <div class="panel-head collapse-head" @click="toggle">
      <h2><span class="chev">▼</span>AI 问答</h2>
      <button class="btn ghost mini-btn" type="button" @click="doClear">清空对话</button>
    </div>
    <div class="collapse-body">
      <div ref="chatBox" class="chat-box">
        <div v-if="!ai.history.length && !ai.streaming && !ai.streamError" class="chat-empty">
          还没有对话。试试问：「这周谁学习最勤快？」「最近大家都在学什么？」
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
      <div class="chat-form">
        <input v-model="input" placeholder="问问大家的学习情况…（AI 会参考全站学习记录作答）" maxlength="2000" @keydown.enter="doSend">
        <button class="btn primary" type="button" :disabled="ai.busy" @click="doSend">发送</button>
      </div>
      <div class="chat-status" :style="ai.statusErr ? 'color:var(--danger)' : ''">{{ ai.status }}</div>
      <div class="ai-settings">
        <div class="row">
          <div class="seg">
            <button type="button" :class="{ active: !isApi }" @click="setProvider('local')">服务器AI</button>
            <button type="button" :class="{ active: isApi }" @click="setProvider('api')">外部 API</button>
          </div>
          <div v-if="!isApi" class="field">
            <label><span>模型</span>
              <select :value="ai.cfg.local_model" @change="setLocalModel($event.target.value)">
                <option value="">{{ autoLabel }}</option>
                <option v-for="m in ai.models" :key="m" :value="m">{{ m }}</option>
              </select>
            </label>
          </div>
        </div>
        <div v-if="isApi">
          <div class="row">
            <div class="field" style="flex:2 1 220px;">
              <label><span>Base URL</span><input v-model="baseUrl" placeholder="https://api.deepseek.com/v1" maxlength="300"></label>
            </div>
            <div class="field" style="flex:2 1 180px;">
              <label><span>API Key</span><input v-model="apiKey" type="password" placeholder="sk-..." maxlength="300" autocomplete="off"></label>
            </div>
          </div>
          <div class="row" style="margin-top:10px;">
            <div class="field">
              <label><span>模型名</span><input v-model="apiModel" placeholder="deepseek-chat / gpt-4o-mini" maxlength="200"></label>
            </div>
            <button class="btn ghost" type="button" @click="saveCfg">保存设置</button>
          </div>
        </div>
        <div class="chat-status" style="margin:0;min-height:0;">{{ cfgHint }}</div>
      </div>
    </div>
  </section>
</template>
