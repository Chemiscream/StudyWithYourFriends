<script setup>
import { watch, nextTick, onMounted, onUnmounted } from "vue";
import { state, EMOJIS, closeEmoji } from "../store.js";

// 插入 emoji：editor 用备份选区；input 用 selectionStart/End
function pick(e) {
  const emoji = e.target.dataset && e.target.dataset.e ? e.target.dataset.e : null;
  if (!emoji) return;
  const em = state.emoji;
  if (em.kind === "editor") {
    // 编辑器：通过全局事件把 emoji 交给 RecordEditor（它持有 contenteditable DOM）
    window.dispatchEvent(new CustomEvent("studylog:emoji", { detail: { emoji } }));
  } else if (em.kind === "input" && em.inputEl) {
    const el = em.inputEl;
    const s = el.selectionStart ?? el.value.length;
    const t = el.selectionEnd ?? el.value.length;
    el.value = el.value.slice(0, s) + emoji + el.value.slice(t);
    el.focus();
    el.setSelectionRange(s + emoji.length, s + emoji.length);
  }
  closeEmoji();
}

function onDocClick(e) {
  if (!state.emoji.visible) return;
  if (e.target.closest(".emoji-panel")) return;
  if (e.target.closest(".emoji-open") || e.target.closest("#pickEmojiBtn")) return;
  closeEmoji();
}

onMounted(() => document.addEventListener("click", onDocClick));
onUnmounted(() => document.removeEventListener("click", onDocClick));

// 面板定位（等价原 toggleEmojiPanel 的视口约束逻辑）
watch(() => state.emoji.visible, (v) => {
  if (!v) return;
  nextTick(() => {
    const el = document.querySelector(".emoji-panel");
    const anchor = state.emoji.anchorEl;
    if (!el || !anchor) return;
    const rect = anchor.getBoundingClientRect();
    const pw = Math.min(320, window.innerWidth * 0.92);
    let left = rect.left;
    if (left + pw > window.innerWidth - 8) left = window.innerWidth - pw - 8;
    if (left < 8) left = 8;
    let top = rect.bottom + 6;
    const ph = 280;
    if (top + ph > window.innerHeight - 8) top = Math.max(8, rect.top - ph - 6);
    el.style.left = left + "px";
    el.style.top = top + "px";
    el.style.maxHeight = ph + "px";
    el.style.overflowY = "auto";
  });
});
</script>

<template>
  <div v-if="state.emoji.visible" class="emoji-panel" @click="pick">
    <button v-for="e in EMOJIS" :key="e" class="emoji-item" type="button" :data-e="e">{{ e }}</button>
  </div>
</template>
