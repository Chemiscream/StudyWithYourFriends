<script setup>
import { ref, watch, nextTick, onMounted, onUnmounted } from "vue";
import { state, submitRecord, cancelEdit, toggleEmoji, askConfirm, ensureUsers } from "../store.js";

const editor = ref(null);      // contenteditable div
const imgInput = ref(null);
const pickEmojiBtn = ref(null);

const ALLOWED_IMG = ["image/png", "image/jpeg", "image/gif", "image/webp"];
const ALLOWED_VIDEO = ["video/mp4", "video/webm", "video/quicktime"];
const MAX_IMG_SIZE = 5 * 1024 * 1024;    // 图片 ≤5MB
const MAX_VIDEO_SIZE = 40 * 1024 * 1024; // 视频 ≤40MB（base64 后约 53MB）

// ---------- @提及补全（富文本编辑器专用：基于光标所在文本节点检测） ----------
const mentionOpen = ref(false);
const mentionItems = ref([]);
const mentionIndex = ref(0);
let mNode = null;  // 光标所在文本节点
let mStart = -1;   // @ 在该文本节点中的偏移

function mQuery() {
  const sel = window.getSelection();
  if (!sel || !sel.isCollapsed || !sel.anchorNode || sel.anchorNode.nodeType !== 3) return null;
  if (!editor.value || !editor.value.contains(sel.anchorNode)) return null;
  const left = sel.anchorNode.textContent.slice(0, sel.anchorOffset);
  const m = left.match(/@([^\s@]{0,32})$/);
  return m ? { query: m[1] } : null;
}
function onEditorInput() {
  const hit = mQuery();
  if (!hit) { mentionOpen.value = false; return; }
  ensureUsers();
  const q = hit.query.toLowerCase();
  mentionItems.value = state.users
    .filter((u) => !q || u.username.toLowerCase().includes(q))
    .slice(0, 20);
  if (!mentionItems.value.length) { mentionOpen.value = false; return; }
  const sel = window.getSelection();
  mNode = sel.anchorNode;
  mStart = sel.anchorOffset - hit.query.length - 1; // 去掉 @ 与查询词
  mentionIndex.value = 0;
  mentionOpen.value = true;
}
function mentionPick(u) {
  if (!u || mNode == null || !editor.value) return;
  const sel = window.getSelection();
  if (!sel || !sel.rangeCount) return;
  if (!editor.value.contains(mNode)) { mentionOpen.value = false; return; } // 触发点节点已被移除（如编辑器被清空）
  const rng = document.createRange();
  rng.setStart(mNode, mStart);
  rng.setEnd(sel.anchorNode, sel.anchorOffset); // @ 到光标 = 未完成的提及片段
  rng.deleteContents();
  const txt = document.createTextNode("@" + u.username + " ");
  rng.insertNode(txt);
  rng.setStart(txt, txt.length);
  rng.collapse(true);
  sel.removeAllRanges();
  sel.addRange(rng);
  mentionOpen.value = false;
  editor.value.focus();
}
function onEditorKeydown(e) {
  if (!mentionOpen.value) return;
  if (e.key === "ArrowDown") { e.preventDefault(); mentionIndex.value = (mentionIndex.value + 1) % mentionItems.value.length; }
  else if (e.key === "ArrowUp") { e.preventDefault(); mentionIndex.value = (mentionIndex.value - 1 + mentionItems.value.length) % mentionItems.value.length; }
  else if (e.key === "Enter") { e.preventDefault(); mentionPick(mentionItems.value[mentionIndex.value]); } // 补全优先于换行
  else if (e.key === "Escape") { mentionOpen.value = false; }
}
function onEditorBlur() {
  setTimeout(() => { mentionOpen.value = false; }, 150); // 先让列表项 mousedown 生效
}

// 编辑态切换时填充/清空编辑器（v-html 不适用于 contenteditable，命令式操作）
watch(() => state.editingId, (id) => {
  mentionOpen.value = false; // 切换时收起补全弹层
  if (id != null && editor.value) {
    editor.value.innerHTML = state.editHtml || "";
    nextTickScroll();
  } else if (editor.value) {
    editor.value.innerHTML = "";
  }
});
function nextTickScroll() {
  nextTick(() => {
    const el = editor.value && editor.value.closest(".panel");
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
    if (editor.value) editor.value.focus();
  });
}

function setMsg(text, cls) { state.addMsg = { text, cls: cls || "" }; }

// ---------- 媒体插入 ----------
function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(r.result);
    r.onerror = reject;
    r.readAsDataURL(file);
  });
}
function makeImg(src) {
  const img = document.createElement("img");
  img.src = src;
  img.alt = "学习图片";
  return img;
}
function makeVideo(src) {
  const v = document.createElement("video");
  v.src = src;
  v.controls = true;
  v.preload = "metadata";
  return v;
}

// 编辑器内最后一次光标位置：未点击编辑器直接插入媒体时，用它在正确位置插入
let lastEditorRange = null;
function onSelectionChange() {
  const sel = window.getSelection();
  if (sel && sel.rangeCount && editor.value && editor.value.contains(sel.anchorNode)) {
    lastEditorRange = sel.getRangeAt(0).cloneRange();
  }
}

function insertNodeAtCursor(node) {
  const el = editor.value;
  const sel = window.getSelection();
  let range;
  if (sel && sel.rangeCount && el.contains(sel.anchorNode)) {
    range = sel.getRangeAt(0);
  } else if (lastEditorRange) {
    range = lastEditorRange;
  } else {
    range = document.createRange();
    range.selectNodeContents(el);
    range.collapse(false);
  }
  el.focus();
  range.deleteContents();
  // 媒体节点后必须跟一个 <br>：否则媒体是最后一个节点时浏览器无处渲染光标（光标"消失"）
  const br = document.createElement("br");
  range.insertNode(br);
  range.insertNode(node);
  range.setStartAfter(node);
  range.collapse(true);
  sel.removeAllRanges();
  sel.addRange(range);
  lastEditorRange = range.cloneRange();
}

async function insertMediaFile(file) {
  const isImg = ALLOWED_IMG.includes(file.type);
  const isVideo = ALLOWED_VIDEO.includes(file.type);
  if (!isImg && !isVideo) {
    setMsg("仅支持 png/jpg/gif/webp 图片或 mp4/webm/mov 视频", "err"); return;
  }
  if (isImg && file.size > MAX_IMG_SIZE) {
    setMsg("图片不能超过 5MB", "err"); return;
  }
  if (isVideo && file.size > MAX_VIDEO_SIZE) {
    setMsg("视频不能超过 40MB", "err"); return;
  }
  try {
    setMsg(isVideo ? "视频读取中…" : "");
    const b64 = await fileToBase64(file);
    insertNodeAtCursor(isVideo ? makeVideo(b64) : makeImg(b64));
    setMsg("");
  } catch (e) {
    setMsg("插入失败", "err");
  }
}

function onImgPicked() {
  const f = imgInput.value.files && imgInput.value.files[0];
  if (f) insertMediaFile(f);
  imgInput.value = "";
}

function pickImg() {
  editor.value.focus();
  imgInput.value.click();
}

function pickEmoji() {
  // 打开面板前聚焦并备份选区，插入时恢复
  editor.value.focus();
  const sel = window.getSelection();
  const saved = sel && sel.rangeCount ? sel.getRangeAt(0).cloneRange() : null;
  toggleEmoji("editor", null, saved, pickEmojiBtn.value);
}

// emoji 面板选中的 emoji 通过全局事件送达
function onEmoji(e) {
  insertEmojiText(e.detail.emoji);
}
function insertEmojiText(emoji) {
  const el = editor.value;
  const sel = window.getSelection();
  sel.removeAllRanges();
  if (state.emoji.savedRange) {
    sel.addRange(state.emoji.savedRange);
  } else {
    const r = document.createRange();
    r.selectNodeContents(el); r.collapse(false);
    sel.addRange(r);
  }
  const range = sel.getRangeAt(0);
  range.deleteContents();
  const node = document.createTextNode(emoji);
  range.insertNode(node);
  range.setStartAfter(node); range.setEndAfter(node);
  sel.removeAllRanges(); sel.addRange(range);
  lastEditorRange = range.cloneRange();
}

// 粘贴图片
function onPaste(e) {
  const items = e.clipboardData && e.clipboardData.items;
  if (!items) return;
  for (const it of items) {
    if (it.type && it.type.startsWith("image/")) {
      e.preventDefault();
      insertMediaFile(it.getAsFile());
      return;
    }
  }
}

// 点击编辑器内图片/视频删除（视频点击会触发播放控制，需按住 Alt 点击删除）
async function onEditorClick(e) {
  const t = e.target;
  if (t.tagName === "IMG" && t.closest(".editor")) {
    if (await askConfirm("删除这张图片？")) t.remove();
  } else if (t.tagName === "VIDEO" && t.closest(".editor") && e.altKey) {
    if (await askConfirm("删除这个视频？")) t.remove();
  }
}

function sanitizeHtml(html) {
  const d = document.createElement("div");
  d.innerHTML = html;
  d.querySelectorAll("script,iframe,object,embed,link,style").forEach(n => n.remove());
  d.querySelectorAll("*").forEach(n => {
    [...n.attributes].forEach(a => {
      if (a.name.startsWith("on")) n.removeAttribute(a.name);
    });
  });
  return d.innerHTML;
}
function getEditorText() {
  const el = editor.value;
  return ((el.innerText || el.textContent || "")).trim();
}

async function submit() {
  const hasText = getEditorText();
  const hasImg = editor.value.querySelector("img,video");
  if (!hasText && !hasImg) {
    setMsg("内容不能为空", "err"); return;
  }
  const res = await submitRecord(sanitizeHtml(editor.value.innerHTML));
  // 新增场景 editingId 一直是 null，watch 不会触发，需手动清空编辑器
  if (res && res.ok) {
    editor.value.innerHTML = "";
    lastEditorRange = null;
  }
}

onMounted(() => {
  document.addEventListener("selectionchange", onSelectionChange);
  window.addEventListener("studylog:emoji", onEmoji);
});
onUnmounted(() => {
  document.removeEventListener("selectionchange", onSelectionChange);
  window.removeEventListener("studylog:emoji", onEmoji);
});
</script>

<template>
  <section class="panel">
    <div class="panel-head">
      <h2>{{ state.editingId ? "编辑记录" : "新增学习记录" }}</h2>
      <button v-if="state.editingId" class="btn ghost" type="button" style="min-height:32px;padding:0 12px;font-size:13px;" @click="cancelEdit">取消编辑</button>
    </div>
    <div class="field editor-wrap">
      <div
        ref="editor"
        class="editor"
        contenteditable="true"
        data-placeholder="今天学了什么？记录一下吧…（支持图片/视频，输入@可提及他人）"
        @paste="onPaste"
        @click="onEditorClick"
        @input="onEditorInput"
        @keydown="onEditorKeydown"
        @blur="onEditorBlur"
      ></div>
      <div v-if="mentionOpen" class="mention-pop editor-mention">
        <button
          v-for="(u, i) in mentionItems" :key="u.username" type="button"
          :class="{ active: i === mentionIndex }"
          @mousedown.prevent="mentionPick(u)"
        >
          <span class="role-tag" :style="{ background: '#' + (u.color || '4a90d9') + '22', color: '#' + (u.color || '4a90d9') }">@{{ u.username }}</span>
        </button>
      </div>
    </div>
    <div class="field">
      <input ref="imgInput" type="file" accept="image/png,image/jpeg,image/gif,image/webp,video/mp4,video/webm,video/quicktime" hidden @change="onImgPicked">
    </div>
    <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;">
      <div style="display:flex;gap:8px;">
        <button class="btn ghost" type="button" @click="pickImg">图片/视频</button>
        <button ref="pickEmojiBtn" id="pickEmojiBtn" class="btn ghost" type="button" title="插入 emoji" @click="pickEmoji">😀</button>
      </div>
      <button class="btn primary" type="button" @click="submit">{{ state.editingId ? "保存修改" : "添加记录" }}</button>
    </div>
    <div class="msg" :class="state.addMsg.cls" style="text-align:right;">{{ state.addMsg.text }}</div>
  </section>
</template>
