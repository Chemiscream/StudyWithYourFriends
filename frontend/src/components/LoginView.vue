<script setup>
import { ref } from "vue";
import { doAuth, showHome } from "../store.js";

const mode = ref("login");
const user = ref("");
const pwd = ref("");
const msg = ref({ text: "", cls: "" });
const busy = ref(false);

function setMode(m) {
  mode.value = m;
  msg.value = { text: "", cls: "" };
}

async function submit() {
  if (busy.value) return;
  busy.value = true;
  msg.value = { text: "…", cls: "" };
  const res = await doAuth(mode.value, user.value.trim(), pwd.value);
  busy.value = false;
  if (!res.ok) msg.value = { text: res.message || "操作失败", cls: "err" };
  // 成功后 showHome()（在 doAuth 内）触发视图切换
}
</script>

<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <img class="auth-logo" src="/logo.svg" alt="学习记录 logo">
      <h1>学习记录</h1>
      <p class="sub">记录每天的学习，和伙伴一起成长</p>
      <div class="auth-tabs">
        <button :class="{ active: mode === 'login' }" type="button" @click="setMode('login')">登录</button>
        <button :class="{ active: mode === 'register' }" type="button" @click="setMode('register')">注册</button>
      </div>
      <div class="field"><label><span>用户名（角色）</span><input v-model="user" autocomplete="username"></label></div>
      <div class="field"><label><span>密码</span><input v-model="pwd" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" @keydown.enter="submit"></label></div>
      <button class="primary-btn" type="button" :disabled="busy" @click="submit">{{ mode === "login" ? "登录" : "注册" }}</button>
      <div class="msg" :class="msg.cls">{{ msg.text }}</div>
      <button class="back-link" type="button" @click="showHome">← 返回首页</button>
    </div>
  </div>
</template>
