<script setup>
import { computed } from "vue";
import { state, closeMemo, addMemo, toggleMemo, deleteMemo } from "../store.js";

const pending = computed(() => (state.memo.items || []).filter((m) => !m.done).length);
</script>

<template>
  <div class="modal-mask" @click.self="closeMemo">
    <div class="modal-card memo-card">
      <div class="memo-head">
        <h2>
          我的备忘录
          <span v-if="state.memo.items.length" class="memo-count">待办 {{ pending }} / 共 {{ state.memo.items.length }}</span>
        </h2>
        <button class="modal-close" type="button" title="关闭" @click="closeMemo">✕</button>
      </div>
      <div class="memo-add">
        <textarea
          v-model="state.memo.text"
          class="memo-input" rows="2" maxlength="300"
          placeholder="记点什么…（Enter 添加，Shift+Enter 换行）"
          @keydown.enter.exact.prevent="addMemo"
        ></textarea>
        <button class="btn primary memo-add-btn" type="button" :disabled="state.memo.busy || !state.memo.text.trim()" @click="addMemo">添加</button>
      </div>
      <div class="memo-list">
        <div v-if="state.memo.loading" class="notif-empty">加载中…</div>
        <div v-else-if="state.memo.msg" class="notif-empty">{{ state.memo.msg }}</div>
        <div v-else-if="!state.memo.items.length" class="notif-empty">还没有条目，写一条试试</div>
        <div v-for="m in state.memo.items" :key="m.id" class="memo-item" :class="{ done: m.done }">
          <button
            class="memo-check" type="button" :class="{ on: m.done }"
            :title="m.done ? '标记为未完成' : '标记为完成'"
            @click="toggleMemo(m)"
          >
            <svg v-if="m.done" viewBox="0 0 24 24"><path d="M5 12.5l4.2 4.2L19 7"/></svg>
          </button>
          <span class="memo-text">{{ m.content }}</span>
          <button class="memo-del" type="button" title="删除这条备忘" @click="deleteMemo(m)">
            <svg viewBox="0 0 24 24"><path d="M5 7h14M9.5 7V5h5v2M7.5 7l.8 12h7.4l.8-12"/></svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
