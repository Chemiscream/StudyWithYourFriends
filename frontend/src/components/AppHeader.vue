<script setup>
import { ref, computed, onUnmounted } from "vue";
import { state, PALETTE, chooseColor, logout, toggleTheme } from "../store.js";

const loggedIn = computed(() => !!state.token);
const myColor = computed(() => state.myColor || "4a90d9");
const paletteOpen = ref(false);

function pickColor(c) {
  paletteOpen.value = false;
  chooseColor(c);
}
function onDocClick(e) {
  if (paletteOpen.value && !e.target.closest("#colorPickerWrap")) paletteOpen.value = false;
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
