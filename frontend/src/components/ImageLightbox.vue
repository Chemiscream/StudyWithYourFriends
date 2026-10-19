<template>
  <div class="lightbox-mask" @click="closeImage">
    <img class="lightbox-img" :src="state.lightbox.src" alt="预览大图" @click.stop />
    <button class="lightbox-close" type="button" title="关闭 (Esc)" @click="closeImage">✕</button>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from "vue";
import { state, closeImage } from "../store.js";

function onKey(e) {
  if (e.key === "Escape" && state.lightbox) closeImage();
}
onMounted(() => window.addEventListener("keydown", onKey));
onUnmounted(() => window.removeEventListener("keydown", onKey));
</script>
