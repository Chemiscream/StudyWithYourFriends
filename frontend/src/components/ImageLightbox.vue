<template>
  <div class="lightbox-mask" @click="closeImage">
    <img class="lightbox-img" :src="state.lightbox.src" alt="预览大图" @click.stop />
    <button
      v-if="count > 1" class="lightbox-nav lightbox-prev" type="button"
      title="上一张 (←)" @click.stop="stepImage(-1)"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
    </button>
    <button
      v-if="count > 1" class="lightbox-nav lightbox-next" type="button"
      title="下一张 (→)" @click.stop="stepImage(1)"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7" /></svg>
    </button>
    <span v-if="count > 1" class="lightbox-count">{{ state.lightbox.idx + 1 }} / {{ count }}</span>
    <button class="lightbox-close" type="button" title="关闭 (Esc)" @click="closeImage">✕</button>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from "vue";
import { state, closeImage, stepImage } from "../store.js";

const count = computed(() => (state.lightbox?.imgs || []).length);

function onKey(e) {
  if (!state.lightbox) return;
  if (e.key === "Escape") closeImage();
  else if (e.key === "ArrowLeft") stepImage(-1);
  else if (e.key === "ArrowRight") stepImage(1);
}
onMounted(() => window.addEventListener("keydown", onKey));
onUnmounted(() => window.removeEventListener("keydown", onKey));
</script>
