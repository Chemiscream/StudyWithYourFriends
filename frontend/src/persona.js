// 五维人格的前端展示常量：与后端 PersonaService 的 STATS / THRESHOLDS 保持一致
export const PERSONA_ORDER = ["knowledge", "courage", "dexterity", "kindness", "charm"];

export const PERSONA_NAMES = {
  knowledge: "知识",
  courage: "勇气",
  dexterity: "灵巧",
  kindness: "体贴",
  charm: "魅力",
};

// 每级称号（LV.1 → LV.5），顺序与后端 STATS 的 titles 一致
export const PERSONA_TITLES = {
  knowledge: ["才疏学浅", "勤奋好学", "知识分子", "博学多闻", "百科字典"],
  courage: ["胆小多疑", "独当一面", "不屈不挠", "大胆无畏", "狮王之心"],
  dexterity: ["笨手笨脚", "普普通通", "专业职人", "大师手艺", "点石成金"],
  kindness: ["不甚关心", "倾听高手", "善解人意", "乐于助人", "圣母慈爱"],
  charm: ["一般程度", "令人在意", "潜力十足", "魅力洋溢", "超凡脱俗"],
};

// 各等级需要的累计点数
export const PERSONA_THRESHOLDS = [0, 40, 120, 300, 700];

// 「计分规则」弹窗里每个维度的说明
export const PERSONA_RULES = [
  {
    key: "knowledge",
    name: "知识",
    items: [
      "发布学习记录：当天第一条 +5，之后每条 +2",
      "每日上限 12 点",
    ],
  },
  {
    key: "courage",
    name: "勇气",
    items: [
      "发布自己的第一条学习记录：+8（每个账号仅一次）",
      "记录里带图片或视频：+3（每日一次）",
      "转发他人记录：+2（每日一次）",
      "距上一条记录满 3 天再次发布：+5（每日一次）",
      "首次主动留言（在他人记录下留言，或到他人档案留言）：+5（每个账号仅一次，不占每日额度）",
      "每日上限 12 点",
    ],
  },
  {
    key: "dexterity",
    name: "灵巧",
    items: [
      "每天首次打卡（发布记录）：+2",
      "连续打卡 3 / 7 / 14 / 30 天：+5 / +10 / +15 / +25",
      "在记录里 @ 其他用户：+2（每日一次）",
      "转发他人记录：+2（每日一次）",
      "日常行为每日上限 12 点；连续打卡里程碑奖励不受该上限限制，全额发放",
    ],
  },
  {
    key: "kindness",
    name: "体贴",
    items: [
      "给他人点赞：+1（每日上限 5 点）",
      "给他人留言：+3；回复他人留言再 +2",
      "留言与回复合计每日上限 6 点",
    ],
  },
  {
    key: "charm",
    name: "魅力",
    items: [
      "被他人点赞：+2（同一人对同一条记录仅一次）",
      "被他人收藏：+3（同一人对同一条记录仅一次）",
      "被留言 / 被回复：+2（各自每日上限 6 点）",
    ],
  },
];

// P5 五维金星的折面取色：10 个三角面按朝向与正反取 6 档，
// 光从左上打来、视线从右下方看，相邻面一亮一暗形成双锥的棱线。
// 外圈星体用偏深的暗金，中心再叠一颗黄金色小星（见 p5CoreColor），
// 对应原作「里面那颗是亮金色、外面星角更暗」的层次
const P5_FACET_TONES = ["#8a4400", "#9d5000", "#ae5c00", "#bf6900", "#cf7700", "#dd8800"];
const P5_CORE_TONES = ["#e89b00", "#f5a900", "#ffb700", "#ffc21b", "#ffcf45", "#ffdf6b"];
function p5Tone(tones, i) {
  const mid = -72 + i * 36;
  const lit = Math.cos(((mid - 245) * Math.PI) / 180);
  const side = i % 2 ? 0.12 : -0.12;
  const b = Math.min(1, Math.max(0, 0.5 + 0.42 * lit + side));
  return tones[Math.min(tones.length - 1, Math.round(b * (tones.length - 1)))];
}
export function p5FacetColor(i) {
  return p5Tone(P5_FACET_TONES, i);
}
export function p5CoreColor(i) {
  return p5Tone(P5_CORE_TONES, i);
}

// 黄金小星 = 等级一时的星形，占比固定；暗金角按等级均分剩余的四段，
// 满级（LV.5）星角伸到最外，几乎铺满灰色底盘
export const GOLD_RATIO = 0.5;
export function p5ArmRatio(rank) {
  const r = Math.min(5, Math.max(1, rank || 1));
  return GOLD_RATIO + (1 - GOLD_RATIO) * (r - 1) / 4;
}

export const PERSONA_COMMON_RULES = [
  "只增不减：取消点赞、取消收藏或删除内容都不会扣分",
  "自己的行为不给自己计分，同一来源只计一次",
  "每日额度按北京时间零点重置",
  "AI 助手不参与任何计分",
];
