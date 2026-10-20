<script setup>
// AI 设置弹窗：切换 服务器AI（本地模型）/ 外部 API，设置仅保存在本浏览器
import { ref, computed } from "vue";
import { state } from "../store.js";
import { ai, setProvider, setLocalModel, saveAiCfgFields, loadModels } from "../useAiChat.js";

const isApi = computed(() => ai.cfg.provider === "api");
const autoLabel = computed(() =>
  ai.models.length ? `自动（${ai.models[0]}）` : "自动");
const cfgHint = computed(() => isApi.value
  ? "支持任意 OpenAI 兼容 API（如 DeepSeek、GPT）。设置只保存在本浏览器；Key 仅在提问时由服务器转发，不会被存储。"
  : "默认使用服务器上的 AI 模型（无需 Key）。模型选「自动」时使用第一个可用模型；也可在下拉框指定。");

const baseUrl = ref(ai.cfg.base_url);
const apiKey = ref(ai.cfg.api_key);
const apiModel = ref(ai.cfg.api_model);

function close() { state.aiSettingsOpen = false; }
function save() { saveAiCfgFields(baseUrl.value, apiKey.value, apiModel.value); }
function switchProvider(p) {
  setProvider(p);
  if (p === "local") loadModels();
}
</script>

<template>
  <div class="modal-mask" @click.self="close">
    <div class="modal-card" role="dialog" aria-modal="true">
      <div class="modal-head">
        <h3>🤖 AI 设置</h3>
        <button class="modal-close" type="button" title="关闭" @click="close">✕</button>
      </div>
      <div class="field" style="margin-bottom:10px;">
        <div class="seg">
          <button type="button" :class="{ active: !isApi }" @click="switchProvider('local')">服务器AI</button>
          <button type="button" :class="{ active: isApi }" @click="switchProvider('api')">外部 API</button>
        </div>
      </div>
      <div v-if="!isApi" class="field">
        <label><span>模型</span>
          <select :value="ai.cfg.local_model" @change="setLocalModel($event.target.value)">
            <option value="">{{ autoLabel }}</option>
            <option v-for="m in ai.models" :key="m" :value="m">{{ m }}</option>
          </select>
        </label>
      </div>
      <template v-else>
        <div class="field">
          <label><span>Base URL</span><input v-model="baseUrl" placeholder="https://api.deepseek.com/v1" maxlength="300"></label>
        </div>
        <div class="field">
          <label><span>API Key</span><input v-model="apiKey" type="password" placeholder="sk-..." maxlength="300" autocomplete="off"></label>
        </div>
        <div class="field">
          <label><span>模型名</span><input v-model="apiModel" placeholder="deepseek-chat / gpt-4o-mini" maxlength="200"></label>
        </div>
        <button class="btn primary" type="button" @click="save">保存设置</button>
      </template>
      <div style="font-size:12px;color:var(--muted);margin-top:10px;">{{ cfgHint }}</div>
    </div>
  </div>
</template>

<style scoped>
.seg { width: 100%; }
.seg button { flex: 1; }
</style>
