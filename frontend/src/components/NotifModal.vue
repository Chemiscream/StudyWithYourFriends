<script setup>
import { state, fmtComment, openNotification, clearNotifications, askConfirm } from "../store.js";

function typeText(t) {
  if (t === "reply") return "回复了你的留言";
  if (t === "like_record") return "赞了你的记录";
  if (t === "like_comment") return "赞了你的留言";
  if (t === "mention") return "在留言中@了你";
  if (t === "quote") return "转发了你的记录";
  if (t === "board") return "在你的留言板留言";
  return "评论了你的记录";
}
async function onClear() {
  if (!state.notif.items.length) return;
  if (!(await askConfirm("确定清除全部消息？"))) return;
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
          <button class="modal-close" type="button" title="关闭" @click="close">✕</button>
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
            <span class="notif-ico" aria-hidden="true">
              <svg v-if="n.type === 'like_record' || n.type === 'like_comment'" viewBox="0 0 24 24"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3z"/><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>
              <svg v-else-if="n.type === 'quote'" viewBox="0 0 24 24"><path d="M17 1.5l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 22.5l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
              <svg v-else-if="n.type === 'mention'" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3.92 7.94"/></svg>
              <svg v-else viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            </span>
            <b>{{ n.actor }}</b> {{ typeText(n.type) }}
            <span v-if="!n.read" class="notif-dot"></span>
          </span>
          <span v-if="n.preview" class="notif-preview">{{ n.preview }}</span>
          <span class="notif-time">{{ fmtComment(n.created_at) }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
