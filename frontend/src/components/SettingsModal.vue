<template>
  <div class="modal-mask" @click.self="close">
    <div class="modal-card settings-card" role="dialog" aria-modal="true">
      <div class="modal-head">
        <h3>⚙️ 设置</h3>
        <button class="modal-close" type="button" title="关闭" @click="close">✕</button>
      </div>
      <div class="settings-list">
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
import { state } from "../store.js";

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
