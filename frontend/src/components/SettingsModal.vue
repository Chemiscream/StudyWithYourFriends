<template>
  <div class="modal-mask" @click.self="close">
    <div class="modal-card settings-card" role="dialog" aria-modal="true">
      <div class="modal-head">
        <h3>⚙️ 设置</h3>
        <button class="modal-close" type="button" title="关闭" @click="close">✕</button>
      </div>
      <div class="settings-list">
        <div class="settings-item settings-theme">
          <span class="settings-icon">🌓</span>
          <span class="settings-text">
            <b>界面主题</b>
            <small>默认浅色 Persona 3 · 深色 Persona 5</small>
          </span>
          <div class="seg theme-seg">
            <button type="button" :class="{ active: theme === 'light' }" @click="setTheme('light')">P3</button>
            <button type="button" :class="{ active: theme === 'dark' }" @click="setTheme('dark')">P5</button>
          </div>
        </div>
        <button class="settings-item" type="button" @click="openProfile">
          <span class="settings-icon">👤</span>
          <span class="settings-text">
            <b>修改个人信息</b>
            <small>修改用户名或密码</small>
          </span>
          <span class="settings-arrow">›</span>
        </button>
        <button class="settings-item" type="button" @click="openChangelog">
          <span class="settings-icon">📋</span>
          <span class="settings-text">
            <b>查看更新日志</b>
            <small>浏览历史版本的全部更新内容</small>
          </span>
          <span class="settings-arrow">›</span>
        </button>
        <button class="settings-item" type="button" @click="openAiSettings">
          <span class="settings-icon">🤖</span>
          <span class="settings-text">
            <b>AI 设置</b>
            <small>切换服务器AI / 外部 API，指定模型</small>
          </span>
          <span class="settings-arrow">›</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { state, applyTheme } from "../store.js";

const theme = ref(document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light");

function setTheme(t) {
  applyTheme(t);
  theme.value = t;
}
function close() {
  state.settingsOpen = false;
}
function openProfile() {
  state.settingsOpen = false;
  state.profileOpen = true;
}
function openChangelog() {
  state.settingsOpen = false;
  state.changelogOpen = true;
}
function openAiSettings() {
  state.settingsOpen = false;
  state.aiSettingsOpen = true;
}
</script>
