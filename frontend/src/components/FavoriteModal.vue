<script setup>
import { state, fmtComment, closeFavorites, openDetail } from "../store.js";

// 点击收藏条目：关闭弹窗并进入该记录的全屏详情页
function onPick(f) {
  state.favorites.open = false;
  openDetail(f.id);
}
function close() {
  closeFavorites();
}
</script>

<template>
  <div class="modal-mask" @click.self="close">
    <div class="modal-card fav-modal-card">
      <div class="fav-modal-head">
        <h2>我的收藏</h2>
        <button class="modal-close" type="button" title="关闭" @click="close">✕</button>
      </div>
      <div class="fav-list">
        <div v-if="state.favorites.loading" class="notif-empty">加载中…</div>
        <div v-else-if="state.favorites.msg" class="notif-empty">{{ state.favorites.msg }}</div>
        <div v-else-if="!state.favorites.items.length" class="notif-empty">还没有收藏的记录</div>
        <button
          v-for="f in state.favorites.items" :key="f.id"
          class="fav-item" type="button"
          @click="onPick(f)"
        >
          <span class="fav-item-head">
            <span class="role-tag" :style="{ background: '#' + (f.color || '4a90d9') + '22', color: '#' + (f.color || '4a90d9') }">{{ f.username }}</span>
            <span class="notif-time">{{ fmtComment(f.created_at) }}<template v-if="f.mine"> · 我</template></span>
          </span>
          <span v-if="f.preview" class="fav-preview">{{ f.preview }}</span>
          <span v-else class="fav-preview fav-preview-empty">（无文字内容）</span>
        </button>
      </div>
    </div>
  </div>
</template>
