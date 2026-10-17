<script setup>
import { ref } from "vue";
import { state, renameUser, changePassword } from "../store.js";

const tab = ref("name");
const newName = ref(state.user);
const oldPwd = ref("");
const p1 = ref("");
const p2 = ref("");
const msg = ref({ text: "", cls: "" });

function setMsg(text, isErr) {
  msg.value = { text, cls: isErr ? "err" : "ok" };
}

async function saveName() {
  const name = newName.value.trim();
  if (!name) { setMsg("用户名不能为空", true); return; }
  if (name === state.user) { setMsg("新用户名与当前用户名相同", true); return; }
  const res = await renameUser(name);
  if (res.ok) {
    setMsg("用户名已更新，所有记录和评论已同步");
    setTimeout(() => (state.profileOpen = false), 900);
  } else {
    setMsg(res.message || "修改失败", true);
  }
}

async function savePwd() {
  if (!oldPwd.value || !p1.value) { setMsg("请填写原密码和新密码", true); return; }
  if (p1.value !== p2.value) { setMsg("两次输入的新密码不一致", true); return; }
  const res = await changePassword(oldPwd.value, p1.value);
  if (res.ok) {
    setMsg("密码已修改，下次登录请使用新密码");
    oldPwd.value = ""; p1.value = ""; p2.value = "";
    setTimeout(() => (state.profileOpen = false), 1200);
  } else {
    setMsg(res.message || "修改失败", true);
  }
}

function close() { state.profileOpen = false; }
function onMask(e) { if (e.target === e.currentTarget) close(); }
</script>

<template>
  <div class="modal-mask" @click="onMask">
    <div class="modal-card">
      <h2>修改个人信息</h2>
      <div class="seg" id="profileTabs">
        <button type="button" :class="{ active: tab === 'name' }" @click="tab = 'name'">修改用户名</button>
        <button type="button" :class="{ active: tab === 'pwd' }" @click="tab = 'pwd'">修改密码</button>
      </div>
      <div v-if="tab === 'name'">
        <div class="field">
          <label><span>新用户名</span><input v-model="newName" maxlength="32" placeholder="最多 32 字"></label>
        </div>
        <button class="btn primary" type="button" @click="saveName">保存用户名</button>
      </div>
      <div v-else>
        <div class="field">
          <label><span>原密码</span><input v-model="oldPwd" type="password" autocomplete="current-password"></label>
        </div>
        <div class="field">
          <label><span>新密码</span><input v-model="p1" type="password" autocomplete="new-password"></label>
        </div>
        <div class="field">
          <label><span>确认新密码</span><input v-model="p2" type="password" autocomplete="new-password"></label>
        </div>
        <button class="btn primary" type="button" @click="savePwd">保存密码</button>
      </div>
      <div class="msg" :class="msg.cls" style="margin-top:10px;">{{ msg.text }}</div>
      <button class="btn ghost" type="button" style="margin-top:4px;" @click="close">关闭</button>
    </div>
  </div>
</template>
