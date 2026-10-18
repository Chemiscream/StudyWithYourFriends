<script setup>
import { state, fmtComment, openNotification, clearNotifications } from "../store.js";

function typeIcon(t) {
  return t === "like_record" || t === "like_comment" ? "❤️" : "💬";
}
function typeText(t) {
  if (t === "reply") return "回复了你的留言";
  if (t === "like_record") return "赞了你的记录";
  if (t === "like_comment") return "赞了你的留言";
  return "评论了你的记录";
}
function onClear() {
  if (!state.notif.items.length) return;
  if (!confirm("确定清除全部消息？")) return;
  clearNotifications();
}
function close() {
  state.notif.open = false;
}
</script>

<template>
  <div class="modal-mask" @click.self="close">
    <div class="modal-card notif-modal-card">
      <div class="notif-modal-head">
        <h2>消息</h2>
        <div class="notif-head-actions">
          <button class="notif-clear" type="button" title="清除全部消息" @click="onClear">清除消息</button>
          <button class="btn ghost notif-close" type="button" title="关闭" @click="close">✕</button>
        </div>
      </div>
      <div class="notif-list">
        <div v-if="!state.notif.items.length" class="notif-empty">暂无消息</div>
        <button
          v-for="n in state.notif.items" :key="n.id"
          class="notif-item" :class="{ unread: !n.read }" type="button"
          @click="openNotification(n)"
        >
          <span class="notif-text">
            {{ typeIcon(n.type) }} <b>{{ n.actor }}</b> {{ typeText(n.type) }}
            <span v-if="!n.read" class="notif-dot"></span>
          </span>
          <span v-if="n.preview" class="notif-preview">{{ n.preview }}</span>
          <span class="notif-time">{{ fmtComment(n.created_at) }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
