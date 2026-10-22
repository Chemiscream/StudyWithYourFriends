<script setup>
import { computed, reactive } from "vue";
import { state, fmtTime, fmtComment, delRecord, delComment, sendComment, startReply, cancelReply, startEdit, startQuote, toggleEmoji, toggleLike, toggleFavorite, toggleCommentLike, openDetail, openImage, openPersona } from "../store.js";
import { renderMention } from "../utils/md.js";
import { useMention } from "../useMention.js";

const mention = useMention(); // 留言 @提及自动补全（全部输入框共用一个弹层状态）

const countInfo = computed(() => (state.records.length ? `共 ${state.records.length} 条` : ""));

// 每条记录的评论输入框内容（key 为记录 id），需响应式才能在发送后清空输入框
const commentDrafts = reactive({});
function draft(r) {
  if (!commentDrafts[r.id]) commentDrafts[r.id] = { text: "" };
  return commentDrafts[r.id];
}
const commentInputs = {};

// 评论折叠：key 为记录 id，true 表示已展开全部
const expanded = reactive({});
function visibleComments(r) {
  const cs = r.comments || [];
  return expanded[r.id] ? cs : cs.slice(0, 3);
}

function replyStateVisible(r) {
  return !!(state.activeReply && state.activeReply.rid === r.id);
}
function commentPlaceholder(r) {
  return replyStateVisible(r) ? "回复 @" + state.activeReply.name + "：" : "友善留言…";
}

function onSend(r) {
  if (mention.takeEnterGuard()) return; // Enter 用于补全时吞掉本次发送
  const d = commentDrafts[r.id];
  const text = d && d.text.trim();
  if (!text) return;
  d.text = "";
  sendComment(r.id, text);
}
function onReply(r, c) {
  startReply(r.id, c.username);
}
function openCommentEmoji(r, e) {
  // 传入被点击的按钮作为定位锚点，否则面板没有位置会出现在视口外
  toggleEmoji("input", commentInputs[r.id], null, e.currentTarget);
}
function registerInput(r, el) {
  if (el) commentInputs[r.id] = el;
}

// 点击记录正文区域进入详情页；链接/按钮/输入框/媒体/评论区/引用卡片不触发；点击图片放大预览
function onRecordClick(r, e) {
  if (e.target.tagName === "IMG") return openImage(e.target.src);
  if (e.target.closest("a, button, input, video, .comments, .record-foot, .quote-card")) return;
  if (window.getSelection().toString()) return; // 正在选择文本时不跳转
  openDetail(r.id);
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
        @click="onRecordClick(r, $event)"
      >
        <div class="record-head">
          <div class="record-user">
            <span class="role-tag name-link" title="查看个人档案" :style="{ background: '#' + (r.color || '4a90d9') + '22', color: '#' + (r.color || '4a90d9') }" @click.stop="openPersona(r.username)">{{ r.username }}</span>
            <span v-if="r.mine" class="record-time">我</span>
          </div>
          <span class="record-time" :title="r.updated_at ? '发布 ' + fmtTime(r.created_at) + '，最后编辑 ' + fmtTime(r.updated_at) : ''">
            {{ fmtTime(r.created_at) }}<template v-if="r.updated_at">（编辑于 {{ fmtTime(r.updated_at) }}）</template>
          </span>
        </div>
        <div class="record-content clickable" v-html="r.content || ''" title="点击查看详情"></div>
        <div v-if="r.quote" class="quote-card" type="button" @click.stop="openDetail(r.quote.id)">
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
            <button class="quote-btn" type="button" title="转发这条记录" @click="startQuote(r)"><svg class="act-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M17 1.5l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 22.5l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg></button>
          </div>
          <div v-if="(r.mine || state.isAdmin) && state.token" class="record-actions">
            <button v-if="r.mine" class="edit-btn" type="button" @click="startEdit(r)">编辑</button>
            <button class="del-btn" type="button" @click="delRecord(r.id)">删除</button>
          </div>
        </div>
        <div class="comments">
          <div v-for="c in visibleComments(r)" :key="c.id" class="comment-item">
            <span class="role-tag name-link" title="查看个人档案" :style="{ background: '#' + c.color + '22', color: '#' + c.color }" @click.stop="openPersona(c.username)">{{ c.username }}</span>
            <span v-if="c.reply_to" class="reply-badge">回复 <b>@{{ c.reply_to }}</b></span>
            <span class="comment-text" v-html="renderMention(c.content)"></span>
            <span class="comment-time">{{ fmtComment(c.created_at) }}</span>
            <button
              class="comment-like" :class="{ liked: c.liked }" type="button"
              :title="state.token ? (c.liked ? '取消点赞' : '点赞') : '登录后可点赞'"
              @click="toggleCommentLike(c.id)"
            ><svg class="act-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3z"/><path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg>{{ c.like_count ? c.like_count : "" }}</button>
            <button v-if="(c.mine || state.isAdmin) && state.token" class="comment-del" type="button" title="删除留言" @click="delComment(c.id)">删除</button>
            <button v-if="state.token && !c.mine" class="comment-reply" type="button" title="回复这条留言" @click="onReply(r, c)">回复</button>
          </div>
          <button
            v-if="(r.comments || []).length > 3 && !expanded[r.id]"
            class="comments-toggle" type="button"
            @click="expanded[r.id] = true"
          >查看全部 {{ r.comments.length }} 条回复 ▾</button>
          <button
            v-if="(r.comments || []).length > 3 && expanded[r.id]"
            class="comments-toggle" type="button"
            @click="expanded[r.id] = false"
          >收起回复 ▴</button>
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
                @input="mention.onInput($event, r.id)"
                @keydown="mention.onKeydown"
                @blur="mention.onBlur"
                @keydown.enter="onSend(r)"
              >
              <div v-if="mention.open && mention.activeKey === r.id" class="mention-pop">
                <button
                  v-for="(u, i) in mention.items" :key="u.username" type="button"
                  :class="{ active: i === mention.index }"
                  @mousedown.prevent="mention.pick(u)"
                >
                  <span class="role-tag" :style="{ background: '#' + (u.color || '4a90d9') + '22', color: '#' + (u.color || '4a90d9') }">@{{ u.username }}</span>
                </button>
              </div>
              <button class="btn ghost emoji-open" type="button" title="插入 emoji" aria-label="插入 emoji" @click="openCommentEmoji(r, $event)"><svg class="smile-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M8.4 14.2a4.6 4.6 0 0 0 7.2 0"/><path d="M9.2 9.8h.01M14.8 9.8h.01"/></svg></button>
              <button class="btn" type="button" @click="onSend(r)">留言</button>
            </div>
          </template>
        </div>
      </article>
    </div>
  </section>
</template>
