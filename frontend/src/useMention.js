// 留言输入框的 @提及自动补全（RecordList / DetailView 共用）。
// 触发：光标左侧存在未结束的 @xxx（到空白为止）→ 弹出匹配用户列表；
// 选择：点击或 Enter；键盘 ↑↓ 切换、Esc 关闭；选完自动补全用户名并加空格。
import { reactive, ref } from "vue";
import { state, ensureUsers } from "./store.js";

export function useMention() {
  const open = ref(false);
  const items = ref([]);
  const index = ref(0);
  const activeKey = ref(null); // 当前补全所属输入框的标识：列表页多个留言框共用本状态，弹层只在匹配的框上渲染
  let input = null;   // 当前补全中的输入框
  let startPos = -1;  // @ 字符在文本中的位置
  let enterGuard = false; // Enter 补全后拦截同一次按键触发的发送

  // 光标左侧是否处于未结束的 @xxx 片段中；返回触发点与查询词
  function queryAt(text, caret) {
    const left = text.slice(0, caret);
    const m = left.match(/@([^\s@]{0,32})$/);
    return m ? { start: caret - m[0].length, query: m[1] } : null;
  }

  function onInput(e, key = null) {
    input = e.target;
    const hit = queryAt(input.value, input.selectionStart);
    if (!hit) { open.value = false; return; }
    ensureUsers();
    const q = hit.query.toLowerCase();
    items.value = state.users
      .filter((u) => !q || u.username.toLowerCase().includes(q))
      .slice(0, 20);
    if (!items.value.length) { open.value = false; return; }
    startPos = hit.start;
    index.value = 0;
    activeKey.value = key;
    open.value = true;
  }

  function pick(u) {
    if (!input || startPos < 0 || !u) return;
    const caret = input.selectionStart;
    const before = input.value.slice(0, startPos) + "@" + u.username + " ";
    input.value = before + input.value.slice(caret);
    // 手动派发 input 让 v-model 同步（与 emoji 插入同一先例）
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.setSelectionRange(before.length, before.length);
    open.value = false;
    input.focus();
  }

  function onKeydown(e) {
    if (!open.value) return;
    if (e.key === "ArrowDown") { e.preventDefault(); index.value = (index.value + 1) % items.value.length; }
    else if (e.key === "ArrowUp") { e.preventDefault(); index.value = (index.value - 1 + items.value.length) % items.value.length; }
    else if (e.key === "Enter") {
      // 拦截发送：补全完成后吞掉同一次 Enter 触发的 onSend
      e.preventDefault();
      enterGuard = true;
      pick(items.value[index.value]);
    }
    else if (e.key === "Escape") { open.value = false; }
  }

  // 组件的 onSend 首行调用：Enter 补全时返回 true 并重置，避免误发留言
  function takeEnterGuard() {
    const v = enterGuard;
    enterGuard = false;
    return v;
  }

  function onBlur() {
    // 延迟关闭：先让列表项的 mousedown 生效
    setTimeout(() => { open.value = false; }, 150);
  }

  // reactive 包装让模板可直接 mention.open（ref 自动解包）
  return reactive({ open, items, index, activeKey, onInput, onKeydown, onBlur, pick, takeEnterGuard });
}
