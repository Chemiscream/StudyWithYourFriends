<script setup>
import { reactive, ref, computed } from "vue";
import { state, fmtTime, fmtComment, closeDetail, delRecord, delComment, sendComment, startReply, cancelReply, startEdit, startQuote, toggleEmoji, toggleLike, toggleFavorite, toggleCommentLike, openDetail, openImage, openPersona } from "../store.js";
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
// 详情页点击图片 → 放大预览
function onContentClick(e) {
  if (e.target.tagName === "IMG") openImage(e.target.src);
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
          <span class="role-tag name-link" title="查看个人档案" :style="{ background: '#' + (r.color || '4a90d9') + '22', color: '#' + (r.color || '4a90d9') }" @click="openPersona(r.username)">{{ r.username }}</span>
          <span v-if="r.mine" class="record-time">我</span>
        </div>
        <span class="record-time" :title="r.updated_at ? '发布 ' + fmtTime(r.created_at) + '，最后编辑 ' + fmtTime(r.updated_at) : ''">
          {{ fmtTime(r.created_at) }}<template v-if="r.updated_at">（编辑于 {{ fmtTime(r.updated_at) }}）</template>
        </span>
      </div>
      <div class="record-content" v-html="r.content || ''" @click="onContentClick"></div>
      <div v-if="r.quote" class="quote-card" type="button" @click="openDetail(r.quote.id)">
        <template v-if="!r.quote.deleted">
          <span class="quote-line"><svg class="quote-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4v8a3 3 0 0 0 3 3h11"/><path d="M15 11l4 4-4 4"/></svg>转发自 <b class="name-link" :style="{ color: '#' + (r.quote.color || '4a90d9') }" title="查看个人档案" @click.stop="openPersona(r.quote.username)">{{ r.quote.username }}</b></span>
          <span class="quote-preview">{{ r.quote.preview || "（无文字内容）" }}</span>
        </template>
        <span v-else class="quote-preview deleted"><svg class="quote-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4v8a3 3 0 0 0 3 3h11"/><path d="M15 11l4 4-4 4"/></svg>原记录已删除</span>
      </div>
      <div class="record-foot">
        <div class="foot-left">
          <button
            class="like-btn" :class="{ liked: r.liked }" type="button"
            :title="state.token ? (r.liked ? '取消点赞' : '点赞') : '登录后可点赞'"
            @click="toggleLike(r.id)"
          ><svg class="act-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3z"/><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>{{ r.like_count || 0 }}</button>
          <button
            class="fav-btn" :class="{ favored: r.favorited }" type="button"
            :title="state.token ? (r.favorited ? '取消收藏' : '收藏') : '登录后可收藏'"
            @click="toggleFavorite(r.id)"
          ><svg class="act-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.6l2.9 5.9 6.5.9-4.7 4.6 1.1 6.5-5.8-3-5.8 3 1.1-6.5-4.7-4.6 6.5-.9z"/></svg></button>
          <button class="quote-btn" type="button" title="转发这条记录" @click="onQuote"><svg class="act-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M17 1.5l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 22.5l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg></button>
        </div>
        <div v-if="(r.mine || state.isAdmin) && state.token" class="record-actions">
          <button v-if="r.mine" class="edit-btn" type="button" @click="onEdit">编辑</button>
          <button class="del-btn" type="button" @click="onDelete">删除</button>
        </div>
      </div>
      <div class="comments">
        <div v-if="!(r.comments || []).length" class="comment-empty">还没有留言</div>
        <div v-for="c in r.comments || []" :key="c.id" class="comment-item">
          <span class="role-tag name-link" title="查看个人档案" :style="{ background: '#' + c.color + '22', color: '#' + c.color }" @click="openPersona(c.username)">{{ c.username }}</span>
          <span v-if="c.reply_to" class="reply-badge">回复 <b>@{{ c.reply_to }}</b></span>
          <span class="comment-text" v-html="renderMention(c.content)"></span>
          <span class="comment-time">{{ fmtComment(c.created_at) }}</span>
          <button
            class="comment-like" :class="{ liked: c.liked }" type="button"
            :title="state.token ? (c.liked ? '取消点赞' : '点赞') : '登录后可点赞'"
            @click="toggleCommentLike(c.id)"
          ><svg class="act-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3z"/><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>{{ c.like_count ? c.like_count : "" }}</button>
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
            <button class="btn ghost emoji-open" type="button" title="插入 emoji" aria-label="插入 emoji" @click="openCommentEmoji"><svg class="smile-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M8.4 14.2a4.6 4.6 0 0 0 7.2 0"/><path d="M9.2 9.8h.01M14.8 9.8h.01"/></svg></button>
            <button class="btn" type="button" @click="onSend">留言</button>
          </div>
        </template>
      </div>
    </article>
  </section>
</template>
