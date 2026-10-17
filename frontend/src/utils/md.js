// 轻量 Markdown 渲染（AI 回答专用）：先整体 HTML 转义再做标记转换，天然防 XSS。
// 支持：粗体/斜体、行内代码、代码块、标题、无序/有序列表、http(s) 链接、段落换行。
export function esc(s) {
  const d = document.createElement("div");
  d.textContent = s;
  return d.innerHTML;
}

export function mdRender(src) {
  src = src.replace(/\s+$/, ""); // 尾部空白不渲染，避免流式输出时出现多余空行
  const blocks = [], spans = [];
  // 1. 提取代码块与行内代码为占位符，其内容不做任何标记转换
  src = src.replace(/```(\w*)\n?([\s\S]*?)(```|$)/g, (m, lang, code) => {
    blocks.push('<pre class="md-code"><code>' + esc(code.replace(/\n$/, "")) + "</code></pre>");
    return "\u0000" + (blocks.length - 1) + "\u0000";
  });
  src = src.replace(/`([^`\n]+)`/g, (m, c) => {
    spans.push('<code class="md-inline">' + esc(c) + "</code>");
    return "\u0001" + (spans.length - 1) + "\u0001";
  });
  // 2. 整体转义后再转换剩余标记
  let h = esc(src);
  h = h.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, (m, text, url) =>
    '<a href="' + url.replace(/"/g, "%22") + '" target="_blank" rel="noopener noreferrer">' + text + "</a>");
  h = h.replace(/\*\*([^*\n]+)\*\*/g, "<strong>$1</strong>");
  h = h.replace(/(^|[^*\w])\*([^*\n]+)\*/g, "$1<em>$2</em>");
  // 3. 逐行组装：标题 / 列表 / 普通段落
  let out = "", inUl = false, inOl = false;
  const closeLists = () => {
    if (inUl) { out += "</ul>"; inUl = false; }
    if (inOl) { out += "</ol>"; inOl = false; }
  };
  for (const line of h.split("\n")) {
    const t = line.trim();
    let m;
    if ((m = t.match(/^#{1,6}\s+(.+)$/))) { closeLists(); out += '<div class="md-h">' + m[1] + "</div>"; }
    else if ((m = t.match(/^[-*]\s+(.+)$/))) {
      if (inOl) { out += "</ol>"; inOl = false; }
      if (!inUl) { out += "<ul>"; inUl = true; }
      out += "<li>" + m[1] + "</li>";
    }
    else if ((m = t.match(/^\d+[.、)]\s+(.+)$/))) {
      if (inUl) { out += "</ul>"; inUl = false; }
      if (!inOl) { out += "<ol>"; inOl = true; }
      out += "<li>" + m[1] + "</li>";
    }
    else if (/^\u0000\d+\u0000$/.test(t)) { closeLists(); out += t; }
    else if (t === "") { closeLists(); out += "<div><br></div>"; }
    else { closeLists(); out += "<div>" + t + "</div>"; }
  }
  closeLists();
  // 4. 还原代码块与行内代码
  out = out.replace(/\u0001(\d+)\u0001/g, (m, i) => spans[+i]);
  out = out.replace(/\u0000(\d+)\u0000/g, (m, i) => blocks[+i]);
  return out;
}
