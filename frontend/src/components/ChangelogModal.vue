<template>
  <div class="modal-mask" @click.self="close">
    <div class="modal-card changelog-card" role="dialog" aria-modal="true">
      <!-- 列表视图：各版本号 + 首条更新摘要 -->
      <template v-if="!detail">
        <div class="modal-head">
          <h3><svg class="modal-title-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5h10a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2z"/><path d="M9 4.5V3h6v1.5"/><path d="M8.5 10h7M8.5 13.5h7M8.5 17h4.5"/></svg>更新日志</h3>
          <button class="modal-close" type="button" title="关闭" @click="close">✕</button>
        </div>
        <div class="changelog-list">
          <button v-for="v in versions" :key="v.version" class="changelog-item" type="button" @click="detail = v">
            <span class="changelog-ver">v{{ v.version }}</span>
            <span class="changelog-summary">{{ v.notes[0] }}</span>
            <span class="settings-arrow"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7"/></svg></span>
          </button>
          <div v-if="!versions.length && loaded" class="changelog-empty">暂无更新日志</div>
        </div>
      </template>
      <!-- 详情视图：该版本全部更新内容 -->
      <template v-else>
        <div class="modal-head">
          <h3>v{{ detail.version }} 更新内容</h3>
          <button class="modal-close" type="button" title="关闭" @click="close">✕</button>
        </div>
        <div class="changelog-list">
          <ul class="changelog-notes">
            <li v-for="(n, i) in detail.notes" :key="i">{{ n }}</li>
          </ul>
          <button class="btn ghost changelog-back" type="button" @click="detail = null"><svg class="inline-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M14.5 5.5l-6.5 6.5 6.5 6.5"/></svg>返回版本列表</button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { state } from "../store.js";

const versions = ref([]);
const loaded = ref(false);
const detail = ref(null);

function close() {
  state.changelogOpen = false;
}

onMounted(async () => {
  try {
    const res = await fetch("/api/versions").then((r) => r.json());
    if (res.ok) versions.value = res.versions || [];
  } catch (e) { /* 打开失败时显示空列表 */ }
  loaded.value = true;
});
</script>
