<script setup>
import { state, dismissUpdate } from "../store.js";
import { esc } from "../utils/md.js";
</script>

<template>
  <div class="modal-mask">
    <div class="modal-card">
      <h2>v{{ state.update.version }} 更新日志</h2>
      <ul>
        <li v-for="(n, i) in state.update.notes" :key="i" v-html="esc(n)"></li>
      </ul>
      <button class="btn primary" type="button" @click="dismissUpdate">我知道了</button>
    </div>
  </div>
</template>
