<template>
  <div v-if="state.dialog.open" class="modal-mask" @click.self="closeDialog(false)">
    <div class="modal-card dialog-card" role="dialog" aria-modal="true">
      <p class="dialog-text">{{ state.dialog.text }}</p>
      <div class="dialog-actions">
        <button class="btn" :class="{ ghost: !state.dialog.confirmMode }" type="button" @click="closeDialog(true)">
          {{ state.dialog.confirmMode ? "确定" : "知道了" }}
        </button>
        <button v-if="state.dialog.confirmMode" class="btn ghost" type="button" @click="closeDialog(false)">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from "vue";
import { state, closeDialog } from "../store.js";

// 确认弹窗：Enter=确定，Esc=取消；纯提示：Enter/Esc 均关闭
function onKey(e) {
  if (!state.dialog.open) return;
  if (e.key === "Enter") { e.preventDefault(); closeDialog(true); }
  else if (e.key === "Escape") { e.preventDefault(); closeDialog(false); }
}
onMounted(() => window.addEventListener("keydown", onKey));
onUnmounted(() => window.removeEventListener("keydown", onKey));
</script>
