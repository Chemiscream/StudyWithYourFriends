<script setup>
import { ref, computed, onUnmounted } from "vue";
import { state, PALETTE, chooseColor, logout, toggleTheme, fmtComment, loadNotifications, openNotification, clearNotifications } from "../store.js";

const loggedIn = computed(() => !!state.token);
const myColor = computed(() => state.myColor || "4a90d9");
const paletteOpen = ref(false);

function pickColor(c) {
  paletteOpen.value = false;
  chooseColor(c);
}

// 通知铃铛
function toggleNotif() {
  state.notif.open = !state.notif.open;
  if (state.notif.open) loadNotifications(); // 每次打开拉最新
}
function onClearNotif() {
  if (!state.notif.items.length) return;
  if (!confirm("确定清除全部消息？")) return;
  clearNotifications();
}

function onDocClick(e) {
  if (paletteOpen.value && !e.target.closest("#colorPickerWrap")) paletteOpen.value = false;
  if (state.notif.open && !e.target.closest("#notifWrap")) state.notif.open = false;
}
document.addEventListener("click", onDocClick);
onUnmounted(() => document.removeEventListener("click", onDocClick));
</script>

<template>
  <header class="app-bar">
    <div class="brand">
      <img class="brand-mark" src="/logo.svg" alt="学习记录 logo">
      <div>
        <h1>学习记录</h1>
        <div class="sub">所有用户的学习动态</div>
      </div>
    </div>
    <div class="user-area">
      <span
        v-if="loggedIn"
        class="user-tag"
        :title="state.isAdmin ? '管理员（可删除任意记录/留言）' : ''"
        :style="{ background: '#' + myColor + '22', color: '#' + myColor }"
      >{{ state.isAdmin ? "👑 " : "" }}{{ state.user }}</span>
      <div v-if="loggedIn" id="notifWrap">
        <button class="icon-btn notif-btn" type="button" title="消息通知" @click.stop="toggleNotif">
          🔔
          <span v-if="state.notif.unread > 0" class="notif-badge">{{ state.notif.unread > 99 ? "99+" : state.notif.unread }}</span>
        </button>
        <div v-if="state.notif.open" class="notif-panel">
          <div class="notif-head">
            <span class="notif-title">消息</span>
            <button class="notif-clear" type="button" title="清除全部消息" @click="onClearNotif">清除消息</button>
          </div>
          <div v-if="!state.notif.items.length" class="notif-empty">暂无消息</div>
          <button
            v-for="n in state.notif.items" :key="n.id"
            class="notif-item" :class="{ unread: !n.read }" type="button"
            @click="openNotification(n)"
          >
            <span class="notif-text">
              <b>{{ n.actor }}</b> {{ n.type === "reply" ? "回复了你" : "评论了你的记录" }}
              <span v-if="!n.read" class="notif-dot"></span>
            </span>
            <span v-if="n.preview" class="notif-preview">{{ n.preview }}</span>
            <span class="notif-time">{{ fmtComment(n.created_at) }}</span>
          </button>
        </div>
      </div>
      <div v-if="loggedIn" id="colorPickerWrap">
        <button id="colorBtn" type="button" title="我的主题色" @click.stop="paletteOpen = !paletteOpen">
          <span id="colorDot" :style="{ background: '#' + myColor }"></span>
        </button>
        <div v-if="paletteOpen" class="color-palette">
          <button
            v-for="c in PALETTE" :key="c" type="button"
            :style="{ background: '#' + c }"
            :class="{ active: c === state.myColor }"
            :title="'#' + c"
            @click="pickColor(c)"
          ></button>
        </div>
      </div>
      <button v-if="loggedIn" class="icon-btn" type="button" title="修改个人信息" @click="state.profileOpen = true">✏️</button>
      <a v-if="!loggedIn" class="btn primary login-link" href="#login" style="min-height:36px;padding:0 16px;font-size:13px;text-decoration:none;display:inline-flex;align-items:center;">登录</a>
      <button class="icon-btn" type="button" title="切换主题" @click="toggleTheme">🌓</button>
      <button v-if="loggedIn" class="btn ghost logout-btn" type="button" title="退出登录" style="min-height:36px;padding:0 14px;font-size:13px;" @click="logout">退出</button>
    </div>
  </header>
</template>
