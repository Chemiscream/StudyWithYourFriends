<script setup>
import { reactive, ref, computed } from "vue";
import { state, fmtTime, fmtComment, closeDetail, delRecord, delComment, sendComment, startReply, cancelReply, startEdit, startQuote, toggleEmoji, toggleLike, toggleFavorite, toggleCommentLike, openDetail } from "../store.js";
import { renderMention } from "../utils/md.js";
import { useMention } from "../useMention.js";

const mention = useMention(); // 留言 @提及自动补全

const r = computed(() => state.detailRecord);

const draft = reactive({ text: "" });
const commentInput = ref(null);

function replyStateVisible() {
  return !!(state.activeReply && r.value && state.activeReply.rid === r.value.id);
}
function commentPlaceholder() {
  return replyStateVisible() ? "回复 @" + state.activeReply.name + "：" : "友善留言…";
}
function onSend() {
  if (mention.takeEnterGuard()) return; // Enter 用于补全时吞掉本次发送
  const text = draft.text.trim();
  if (!text || !r.value) return;
  draft.text = "";
  sendComment(r.value.id, text);
}
function onReply(c) {
  startReply(r.value.id, c.username);
  commentInput.value && commentInput.value.focus();
}
function openCommentEmoji(e) {
  // 传入被点击的按钮作为定位锚点，否则面板没有位置会出现在视口外
  toggleEmoji("input", commentInput.value, null, e.currentTarget);
}
function onEdit() {
  const rec = r.value;
  state.detailReturnScroll = null; // 编辑回主页编辑器顶部，不恢复列表滚动位置
  closeDetail();
  startEdit(rec);
}
function onDelete() {
  delRecord(r.value.id); // 删除成功后 store 会自动关闭详情页
}
function onQuote() {
  const rec = r.value;
  closeDetail();
  startQuote(rec); // 回主页顶部编辑器以转发模式打开
}
</script>

<template>
  <section class="detail-view">
    <div class="panel-head detail-head">
      <button class="btn ghost back-btn" type="button" title="返回列表" @click="closeDetail">← 返回</button>
      <h2>记录详情</h2>
    </div>
    <div v-if="state.detailLoading && !r" class="empty">加载中…</div>
    <div v-else-if="state.detailMsg" class="empty">{{ state.detailMsg }}</div>
    <article
      v-else-if="r" :key="r.id"
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
      <div v-if="r.quote" class="quote-card" type="button" @click="openDetail(r.quote.id)">
        <template v-if="!r.quote.deleted">
          <span class="quote-line">↪ 转发自 <b :style="{ color: '#' + (r.quote.color || '4a90d9') }">{{ r.quote.username }}</b></span>
          <span class="quote-preview">{{ r.quote.preview || "（无文字内容）" }}</span>
        </template>
        <span v-else class="quote-preview deleted">↪ 原记录已删除</span>
      </div>
      <div class="record-foot">
        <div class="foot-left">
          <button
            class="like-btn" :class="{ liked: r.liked }" type="button"
            :title="state.token ? (r.liked ? '取消点赞' : '点赞') : '登录后可点赞'"
            @click="toggleLike(r.id)"
          >👍 {{ r.like_count || 0 }}</button>
          <button
            class="fav-btn" :class="{ favored: r.favorited }" type="button"
            :title="state.token ? (r.favorited ? '取消收藏' : '收藏') : '登录后可收藏'"
            @click="toggleFavorite(r.id)"
          >{{ r.favorited ? "⭐" : "☆" }}</button>
          <button class="quote-btn" type="button" title="转发这条记录" @click="onQuote">🔁</button>
        </div>
        <div v-if="(r.mine || state.isAdmin) && state.token" class="record-actions">
          <button v-if="r.mine" class="edit-btn" type="button" @click="onEdit">编辑</button>
          <button class="del-btn" type="button" @click="onDelete">删除</button>
        </div>
      </div>
      <div class="comments">
        <div v-if="!(r.comments || []).length" class="comment-empty">还没有留言</div>
        <div v-for="c in r.comments || []" :key="c.id" class="comment-item">
          <span class="role-tag" :style="{ background: '#' + c.color + '22', color: '#' + c.color }">{{ c.username }}</span>
          <span v-if="c.reply_to" class="reply-badge">回复 <b>@{{ c.reply_to }}</b></span>
          <span class="comment-text" v-html="renderMention(c.content)"></span>
          <span class="comment-time">{{ fmtComment(c.created_at) }}</span>
          <button
            class="comment-like" :class="{ liked: c.liked }" type="button"
            :title="state.token ? (c.liked ? '取消点赞' : '点赞') : '登录后可点赞'"
            @click="toggleCommentLike(c.id)"
          >👍{{ c.like_count ? " " + c.like_count : "" }}</button>
          <button v-if="(c.mine || state.isAdmin) && state.token" class="comment-del" type="button" title="删除留言" @click="delComment(c.id)">删除</button>
          <button v-if="state.token && !c.mine" class="comment-reply" type="button" title="回复这条留言" @click="onReply(c)">回复</button>
        </div>
        <template v-if="state.token">
          <div v-if="replyStateVisible()" class="reply-state">
            回复 <b>@{{ state.activeReply.name }}</b>
            <button class="reply-cancel" type="button" title="取消回复" @click="cancelReply">✕</button>
          </div>
          <div class="comment-form">
            <input
              class="input" ref="commentInput"
              v-model="draft.text"
              :placeholder="commentPlaceholder()"
              maxlength="2000"
              @input="mention.onInput"
              @keydown="mention.onKeydown"
              @blur="mention.onBlur"
              @keydown.enter="onSend"
            >
            <div v-if="mention.open" class="mention-pop">
              <button
                v-for="(u, i) in mention.items" :key="u.username" type="button"
                :class="{ active: i === mention.index }"
                @mousedown.prevent="mention.pick(u)"
              >
                <span class="role-tag" :style="{ background: '#' + (u.color || '4a90d9') + '22', color: '#' + (u.color || '4a90d9') }">@{{ u.username }}</span>
              </button>
            </div>
            <button class="btn ghost emoji-open" type="button" title="插入 emoji" @click="openCommentEmoji">😀</button>
            <button class="btn" type="button" @click="onSend">留言</button>
          </div>
        </template>
      </div>
    </article>
  </section>
</template>
