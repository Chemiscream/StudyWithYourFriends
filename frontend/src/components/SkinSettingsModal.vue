<script setup>
import { state, applySkin, applyTheme } from "../store.js";

const OPTIONS = [
  {
    key: "default",
    name: "Persona",
    en: "PERSONA",
    desc: "保留原始 Persona 3 / Persona 5 视觉，可继续切换明暗主题。",
  },
  {
    key: "aero",
    name: "Frutiger Aero",
    en: "FRUTIGER AERO",
    desc: "蓝天、玻璃标题栏与立体控件，固定使用明亮白天界面。",
  },
];

function close() {
  state.skinSettingsOpen = false;
}

function pick(option) {
  applySkin(option.key);
  applyTheme("light");
  state.skinSettingsOpen = false;
}
</script>

<template>
  <div class="modal-mask" @click.self="close">
    <div class="modal-card skin-settings-card" role="dialog" aria-modal="true" aria-label="皮肤预置">
      <div class="modal-head">
        <h3>皮肤预置</h3>
        <button class="modal-close" type="button" title="关闭" @click="close">✕</button>
      </div>

      <p class="skin-settings-lead">选择整套界面视觉。切换后会立即应用，并保存在当前浏览器中。</p>

      <div class="skin-options" role="list">
        <button
          v-for="option in OPTIONS"
          :key="option.key"
          class="skin-option"
          :class="{ active: state.skin === option.key }"
          type="button"
          role="listitem"
          @click="pick(option)"
        >
          <span class="skin-swatch" :class="option.key" aria-hidden="true"><i></i></span>
          <span class="skin-option-text">
            <b>{{ option.name }}</b>
            <small>{{ option.en }}</small>
            <em>{{ option.desc }}</em>
          </span>
          <span class="skin-option-state">{{ state.skin === option.key ? "使用中" : "切换" }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.skin-settings-card { width: min(440px, 94vw); }
.skin-settings-lead {
  margin: -2px 0 12px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
.skin-options { display: grid; gap: 8px; }
.skin-option {
  display: flex; align-items: center; gap: 12px;
  width: 100%; padding: 10px 11px;
  border: 1px solid var(--line-strong);
  background: var(--panel-soft);
  color: var(--ink);
  text-align: left;
  font: inherit;
  white-space: normal;
  cursor: pointer;
}
.skin-option:hover { border-color: var(--primary); }
.skin-option.active { border-color: var(--primary); background: var(--primary-soft); }
.skin-swatch {
  position: relative; flex: none;
  width: 58px; height: 38px;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  background: #f4f6f8;
}
.skin-swatch.default { background: linear-gradient(135deg, #f7f3e6 0 52%, #121212 52% 100%); }
.skin-swatch.default::after {
  content: ""; position: absolute; left: 5px; bottom: 6px;
  width: 25px; height: 3px; background: #e60012;
}
.skin-swatch.aero {
  background:
    linear-gradient(180deg, transparent 0 58%, #6cbb45 58% 100%),
    linear-gradient(180deg, #3f9fdd 0%, #9ed7f4 58%, #eefaff 100%);
}
.skin-swatch.aero::after {
  content: ""; position: absolute; left: 6px; top: 7px; right: 6px; height: 7px;
  border: 1px solid #4f8fc9;
  background: linear-gradient(180deg, #f7fbff, #b9dcf2 55%, #7fb4d8);
}
.skin-swatch.aero i {
  position: absolute; right: 7px; bottom: 5px;
  width: 11px; height: 11px; border-radius: 50%;
  background: radial-gradient(circle at 32% 28%, #fff, #cbe9fb 32%, #5aa6d4 72%);
}
.skin-option-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.skin-option-text b { font-size: 13px; }
.skin-option-text small {
  font-family: var(--mono);
  font-size: 8px; letter-spacing: .12em; color: var(--primary-dark);
}
.skin-option-text em {
  margin-top: 2px; font-style: normal;
  font-size: 11px; line-height: 1.45; color: var(--muted);
}
.skin-option-state {
  flex: none; min-width: 44px; text-align: right;
  font-size: 11px; color: var(--muted); white-space: nowrap;
}
.skin-option.active .skin-option-state { color: var(--primary-dark); font-weight: 700; }
</style>
