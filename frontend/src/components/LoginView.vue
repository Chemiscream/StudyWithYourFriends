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
    <div class="auth-visual" aria-hidden="true">
      <span class="eyebrow">SEES STUDY ARCHIVE · EST. 2026</span>
      <h2 class="auth-wordmark">STUDY<span>//</span>LOG</h2>
      <p class="auth-tagline">THE DAY<br>IS YOURS.</p>
      <div class="auth-code">FILE NO. 003<br>ACCESS LEVEL: FRIENDS</div>
    </div>
    <div class="auth-card">
      <span class="eyebrow auth-eyebrow">WELCOME BACK</span>
      <h1>{{ mode === "login" ? "登录档案" : "建立档案" }}</h1>
      <p class="sub">记录每天的学习，和伙伴一起成长</p>
      <div class="auth-tabs">
        <button :class="{ active: mode === 'login' }" type="button" @click="setMode('login')">登录</button>
        <button :class="{ active: mode === 'register' }" type="button" @click="setMode('register')">注册</button>
      </div>
      <div class="field"><label><span>USER NAME / 用户名</span><input v-model="user" autocomplete="username"></label></div>
      <div class="field"><label><span>PASSWORD / 密码</span><input v-model="pwd" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" @keydown.enter="submit"></label></div>
      <button class="primary-btn" type="button" :disabled="busy" @click="submit">
        {{ mode === "login" ? "ENTER STUDY//LOG →" : "CREATE ARCHIVE →" }}
      </button>
      <div class="msg" :class="msg.cls">{{ msg.text }}</div>
      <button class="back-link" type="button" @click="showHome">← 返回首页</button>
    </div>
  </div>
</template>
