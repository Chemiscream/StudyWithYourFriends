<script setup>
import { ref, computed, onUnmounted } from "vue";
import { state, PALETTE, chooseColor, logout, toggleTheme, loadNotifications, openFavorites } from "../store.js";

const loggedIn = computed(() => !!state.token);
const myColor = computed(() => state.myColor || "4a90d9");
const paletteOpen = ref(false);
const theme = ref(document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light");

function pickColor(c) {
  paletteOpen.value = false;
  chooseColor(c);
}

function switchTheme() {
  toggleTheme();
  theme.value = document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light";
}

// 通知铃铛：点击打开消息弹窗（NotifModal）
function toggleNotif() {
  state.notif.open = true;
  loadNotifications(); // 每次打开拉最新
}

function onDocClick(e) {
  if (paletteOpen.value && !e.target.closest("#colorPickerWrap")) paletteOpen.value = false;
}
function onThemeEvent(e) {
  theme.value = e.detail === "dark" ? "dark" : "light";
}
document.addEventListener("click", onDocClick);
window.addEventListener("studylog:theme", onThemeEvent);
onUnmounted(() => {
  document.removeEventListener("click", onDocClick);
  window.removeEventListener("studylog:theme", onThemeEvent);
});
</script>

<template>
  <header class="app-bar">
    <div class="brand">
      <svg class="brand-emblem" viewBox="0 0 48 48" aria-hidden="true">
        <circle cx="24" cy="24" r="20" fill="none" stroke="currentColor" stroke-width="1.4"/>
        <path d="M14 33L34 15" stroke="currentColor" stroke-width="2.6"/>
        <circle cx="32" cy="13" r="2.2" fill="currentColor" stroke="none"/>
        <path d="M13 38h22" stroke="currentColor" stroke-width="1" opacity=".5"/>
      </svg>
      <span class="brand-text">
        <b>STUDY<span class="sl">//</span>LOG</b>
        <small>SEES STUDY ARCHIVE · 学习记录</small>
      </span>
    </div>
    <div class="header-mid" aria-hidden="true">
      <span class="hm-line"></span>
      <span class="hm-text">LEARNING ARCHIVE<br>STUDY WITH YOUR FRIENDS</span>
    </div>
    <div class="user-area">
      <div v-if="loggedIn" id="colorPickerWrap">
        <button id="colorBtn" class="hbtn color-btn" type="button" title="我的主题色" @click.stop="paletteOpen = !paletteOpen">
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
      <span
        v-if="loggedIn"
        class="user-tag"
        :title="state.isAdmin ? '管理员（可删除任意记录/留言）' : ''"
        :style="{ background: '#' + myColor + '22', color: '#' + myColor }"
      >{{ state.isAdmin ? "👑 " : "" }}{{ state.user }}</span>
      <button v-if="loggedIn" class="hbtn" type="button" title="我的收藏" @click="openFavorites">
        <svg class="ico" viewBox="0 0 24 24"><path d="M12 3.6l2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z"/></svg>
        <span class="hbtn-label">收藏</span>
      </button>
      <span v-if="loggedIn" class="notif-wrap">
        <button class="hbtn notif-btn" type="button" title="消息通知" @click="toggleNotif">
          <svg class="ico" viewBox="0 0 24 24"><path d="M18 9a6 6 0 1 0-12 0c0 5-2 6-2 6h16s-2-1-2-6"/><path d="M10 20a2 2 0 0 0 4 0"/></svg>
          <span class="hbtn-label">消息</span>
        </button>
        <span v-if="state.notif.unread > 0" class="notif-badge">{{ state.notif.unread > 99 ? "99+" : state.notif.unread }}</span>
      </span>
      <a v-if="!loggedIn" class="hbtn login-link" href="#login">登录</a>
      <button class="hbtn" type="button" :title="theme === 'dark' ? '切换到浅色（Persona 3）' : '切换到深色（Persona 5）'" @click="switchTheme">
        <svg v-if="theme === 'dark'" class="ico" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4.2"/><path d="M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.2 5.2l1.6 1.6M17.2 17.2l1.6 1.6M18.8 5.2l-1.6 1.6M6.8 17.2l-1.6 1.6"/></svg>
        <svg v-else class="ico" viewBox="0 0 24 24"><path d="M20.5 14.6A8.5 8.5 0 1 1 9.4 3.5a6.8 6.8 0 0 0 11.1 11.1z"/></svg>
        <span class="hbtn-label">主题</span>
      </button>
      <button v-if="loggedIn" class="hbtn" type="button" title="设置" @click="state.settingsOpen = true">
        <svg class="ico" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M19.3 14.7a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.2a1.6 1.6 0 0 0-1-1.5 1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.2a1.6 1.6 0 0 0 1.5-1 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3h.1a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.2a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8v.1a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.2a1.6 1.6 0 0 0-1.5 1z"/></svg>
        <span class="hbtn-label">设置</span>
      </button>
      <button v-if="loggedIn" class="hbtn quit" type="button" title="退出登录" @click="logout">
        <svg class="ico" viewBox="0 0 24 24"><path d="M12 3v9"/><path d="M6.5 6.5a8 8 0 1 0 11 0"/></svg>
        <span class="hbtn-label">退出</span>
      </button>
    </div>
  </header>
</template>
