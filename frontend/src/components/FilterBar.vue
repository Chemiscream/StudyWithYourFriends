<script setup>
import { reactive } from "vue";
import { search, loadRecords, state } from "../store.js";

const f = reactive({ q: state.filter.q, from: state.filter.from, to: state.filter.to });

async function doSearch() {
  await search(f);
}
async function doReset() {
  f.q = ""; f.from = ""; f.to = "";
  state.filter = { q: "", from: "", to: "" };
  await loadRecords();
}
</script>

<template>
  <section class="panel">
    <div class="panel-head"><h2>查询筛选</h2></div>
    <div class="filter-bar">
      <div class="field">
        <label><span>角色 / 内容</span><input v-model="f.q" placeholder="按角色名或记录内容模糊搜索" @keydown.enter="doSearch"></label>
      </div>
      <div class="field date">
        <label><span>起始日期</span><input v-model="f.from" type="date"></label>
      </div>
      <div class="field date">
        <label><span>截止日期</span><input v-model="f.to" type="date"></label>
      </div>
      <button class="btn primary" type="button" @click="doSearch">查询</button>
      <button class="btn ghost" type="button" @click="doReset">重置</button>
    </div>
  </section>
</template>
