/**
 * 健康分共享模块
 * 记录结构 health_record = { date, score, streak, doneToday, total, history }
 * - date: 当前记录所属日期（YYYY-MM-DD）
 * - score: 今日健康分，每天清零
 * - streak: 连续打卡天数（当天有完成行为才算当天）
 * - doneToday: 今日已完成次数（用于判断当天首次打卡）
 * - total: 累计完成次数
 * - history: 近 7 天每日数据 { 'YYYY-MM-DD': { score, done } }，用于周报趋势
 */

const DAY_MS = 24 * 60 * 60 * 1000;
const STORAGE_KEY = 'health_record';
const HISTORY_DAYS = 7;
const WEEK_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function formatDate(ts) {
  const d = new Date(ts);
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

// 只保留最近 HISTORY_DAYS 天的历史（含今天）
function trimHistory(history, today) {
  const cutoff = formatDate(Date.now() - (HISTORY_DAYS - 1) * DAY_MS);
  const trimmed = {};
  Object.keys(history).forEach(key => {
    if (key >= cutoff && key <= today) trimmed[key] = history[key];
  });
  return trimmed;
}

// 兼容旧版本数据（无 history 字段），补齐当天条目
function normalize(record, today) {
  let changed = false;
  if (!record.history) {
    record.history = {};
    changed = true;
  }
  if (!record.history[today]) {
    record.history[today] = { score: record.score || 0, done: record.doneToday || 0 };
    changed = true;
  }
  if (changed) {
    record.history = trimHistory(record.history, today);
    wx.setStorageSync(STORAGE_KEY, record);
  }
  return record;
}

/**
 * 获取今天的健康记录。跨天时自动结算：昨天有完成则保留连续天数，否则清零。
 */
function getTodayRecord() {
  const today = formatDate(Date.now());
  const record = wx.getStorageSync(STORAGE_KEY);

  if (record && record.date === today) return normalize(record, today);

  const yesterday = formatDate(Date.now() - DAY_MS);
  const carriedStreak = record && record.score > 0 && record.date === yesterday
    ? record.streak
    : 0;

  const history = record ? trimHistory(record.history || {}, today) : {};
  const fresh = {
    date: today,
    score: 0,
    streak: carriedStreak,
    doneToday: 0,
    total: record ? record.total || 0 : 0,
    history: Object.assign({}, history, {
      [today]: { score: 0, done: 0 }
    })
  };
  wx.setStorageSync(STORAGE_KEY, fresh);
  return fresh;
}

/**
 * 累加健康分。当天首次完成时连续天数 +1。
 * 返回更新后的完整记录。
 */
function addScore(points) {
  const record = getTodayRecord();
  if (record.doneToday === 0) record.streak += 1;
  record.score += points;
  record.doneToday += 1;
  record.total += 1;
  record.history[record.date] = { score: record.score, done: record.doneToday };
  wx.setStorageSync(STORAGE_KEY, record);
  return record;
}

/**
 * 近 7 天数据（含今天），用于周报趋势图。
 * 返回 [{ date, label, score, done, isToday, height }]，height 为柱状图高度(rpx)。
 */
function getWeekHistory(barMaxHeight) {
  const maxBar = barMaxHeight || 160;
  const record = getTodayRecord();
  const days = [];

  for (let i = HISTORY_DAYS - 1; i >= 0; i--) {
    const d = new Date(Date.now() - i * DAY_MS);
    const key = formatDate(d.getTime());
    const entry = (record.history && record.history[key]) || { score: 0, done: 0 };
    days.push({
      date: key,
      label: WEEK_LABELS[d.getDay()],
      score: entry.score || 0,
      done: entry.done || 0,
      isToday: i === 0
    });
  }

  const maxScore = Math.max.apply(null, days.map(d => d.score).concat([1]));
  days.forEach(d => {
    d.height = d.score > 0 ? Math.max(16, Math.round(d.score / maxScore * maxBar)) : 6;
  });
  return days;
}

module.exports = {
  getTodayRecord,
  addScore,
  getWeekHistory
};
