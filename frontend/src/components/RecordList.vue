<script setup>
import { computed, reactive } from "vue";
import { state, fmtTime, fmtComment, delRecord, delComment, sendComment, startReply, cancelReply, startEdit, toggleEmoji } from "../store.js";

const countInfo = computed(() => (state.records.length ? `共 ${state.records.length} 条` : ""));

// 每条记录的评论输入框内容（key 为记录 id），需响应式才能在发送后清空输入框
const commentDrafts = reactive({});
function draft(r) {
  if (!commentDrafts[r.id]) commentDrafts[r.id] = { text: "" };
  return commentDrafts[r.id];
}
const commentInputs = {};

function replyStateVisible(r) {
  return !!(state.activeReply && state.activeReply.rid === r.id);
}
function commentPlaceholder(r) {
  return replyStateVisible(r) ? "回复 @" + state.activeReply.name + "：" : "友善留言…";
}

function onSend(r) {
  const d = commentDrafts[r.id];
  const text = d && d.text.trim();
  if (!text) return;
  d.text = "";
  sendComment(r.id, text);
}
function onReply(r, c) {
  startReply(r.id, c.username);
}
function openCommentEmoji(r) {
  toggleEmoji("input", commentInputs[r.id], null, null);
}
function registerInput(r, el) {
  if (el) commentInputs[r.id] = el;
}
</script>

<template>
  <section>
    <div class="panel-head" style="margin-bottom:12px;">
      <h2>学习动态</h2>
      <span class="record-time">{{ countInfo }}</span>
    </div>
    <div class="record-list">
      <div v-if="!state.records.length" class="empty">暂无记录</div>
      <article
        v-for="r in state.records" :key="r.id"
        class="record" :class="{ mine: r.mine }"
        :style="{ borderLeftColor: '#' + (r.color || '4a90d9') }"
      >
        <div class="record-head">
          <div class="record-user">
            <span class="role-tag" :style="{ background: '#' + (r.color || '4a90d9') + '22', color: '#' + (r.color || '4a90d9') }">{{ r.username }}</span>
            <span v-if="r.mine" class="record-time">我</span>
          </div>
          <span class="record-time" :title="r.updated_at ? '发布 ' + fmtTime(r.created_at) + '，最后编辑 ' + fmtTime(r.updated_at) : ''">
            {{ fmtTime(r.created_at) }}<template v-if="r.updated_at">（编辑于 {{ fmtTime(r.updated_at) }}）</template>
          </span>
        </div>
        <div class="record-content" v-html="r.content || ''"></div>
        <div v-if="(r.mine || state.isAdmin) && state.token" class="record-actions">
          <button v-if="r.mine" class="edit-btn" type="button" @click="startEdit(r)">编辑</button>
          <button class="del-btn" type="button" @click="delRecord(r.id)">删除</button>
        </div>
        <div class="comments">
          <div v-for="c in r.comments || []" :key="c.id" class="comment-item">
            <span class="role-tag" :style="{ background: '#' + c.color + '22', color: '#' + c.color }">{{ c.username }}</span>
            <span v-if="c.reply_to" class="reply-badge">回复 <b>@{{ c.reply_to }}</b></span>
            <span class="comment-text">{{ c.content }}</span>
            <span class="comment-time">{{ fmtComment(c.created_at) }}</span>
            <button v-if="(c.mine || state.isAdmin) && state.token" class="comment-del" type="button" title="删除留言" @click="delComment(c.id)">删除</button>
            <button v-if="state.token && !c.mine" class="comment-reply" type="button" title="回复这条留言" @click="onReply(r, c)">回复</button>
          </div>
          <template v-if="state.token">
            <div v-if="replyStateVisible(r)" class="reply-state">
              回复 <b>@{{ state.activeReply.name }}</b>
              <button class="reply-cancel" type="button" title="取消回复" @click="cancelReply">✕</button>
            </div>
            <div class="comment-form">
              <input
                class="input" :ref="(el) => registerInput(r, el)"
                v-model="draft(r).text"
                :placeholder="commentPlaceholder(r)"
                maxlength="2000"
                @keydown.enter="onSend(r)"
              >
              <button class="btn ghost emoji-open" type="button" title="插入 emoji" @click="openCommentEmoji(r)">😀</button>
              <button class="btn" type="button" @click="onSend(r)">留言</button>
            </div>
          </template>
        </div>
      </article>
    </div>
  </section>
</template>
