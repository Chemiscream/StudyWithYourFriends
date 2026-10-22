<template>
  <div class="modal-mask" @click.self="close">
    <div class="modal-card settings-card" role="dialog" aria-modal="true">
      <div class="modal-head">
        <h3>
          <svg class="settings-head-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.3 14.7a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.2a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.2a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3h.1a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.2a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8v.1a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.2a1.6 1.6 0 0 0-1.5 1z"/></svg>
          设置
        </h3>
        <button class="modal-close" type="button" title="关闭" @click="close">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>
        </button>
      </div>
      <div class="settings-list">
        <button class="settings-item" type="button" @click="openProfile">
          <span class="settings-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="3.6"/><path d="M4.5 20c0-4 3.4-6.2 7.5-6.2s7.5 2.2 7.5 6.2"/></svg>
          </span>
          <span class="settings-text">
            <b>修改个人信息</b>
            <small>修改用户名或密码</small>
          </span>
          <span class="settings-arrow" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7"/></svg>
          </span>
        </button>
        <button class="settings-item" type="button" @click="openChangelog">
          <span class="settings-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M5 3.5h9l5 5v12H5z"/><path d="M14 3.5v5h5"/><path d="M8.5 12.5h7M8.5 16h5"/></svg>
          </span>
          <span class="settings-text">
            <b>查看更新日志</b>
            <small>浏览历史版本的全部更新内容</small>
          </span>
          <span class="settings-arrow" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7"/></svg>
          </span>
        </button>
        <button class="settings-item" type="button" @click="openAiSettings">
          <span class="settings-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24"><rect x="4" y="7.5" width="16" height="11" rx="1"/><path d="M12 3.5v4"/><path d="M9 12h.01M15 12h.01"/><path d="M9.5 16h5"/></svg>
          </span>
          <span class="settings-text">
            <b>AI 设置</b>
            <small>切换服务器AI / 外部 API，指定模型</small>
          </span>
          <span class="settings-arrow" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7"/></svg>
          </span>
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
