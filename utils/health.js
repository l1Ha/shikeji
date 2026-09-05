/**
 * 健康分共享模块
 * 记录结构 health_record = { date, score, streak, doneToday, total }
 * - date: 当前记录所属日期（YYYY-MM-DD）
 * - score: 今日健康分，每天清零
 * - streak: 连续打卡天数（当天有完成行为才算当天）
 * - doneToday: 今日已完成次数（用于判断当天首次打卡）
 * - total: 累计完成次数
 */

const DAY_MS = 24 * 60 * 60 * 1000;
const STORAGE_KEY = 'health_record';

function formatDate(ts) {
  const d = new Date(ts);
  const m = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

/**
 * 获取今天的健康记录。跨天时自动结算：昨天有完成则保留连续天数，否则清零。
 */
function getTodayRecord() {
  const today = formatDate(Date.now());
  const record = wx.getStorageSync(STORAGE_KEY);

  if (record && record.date === today) return record;

  const yesterday = formatDate(Date.now() - DAY_MS);
  const carriedStreak = record && record.score > 0 && record.date === yesterday
    ? record.streak
    : 0;

  const fresh = {
    date: today,
    score: 0,
    streak: carriedStreak,
    doneToday: 0,
    total: record ? record.total || 0 : 0
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
  wx.setStorageSync(STORAGE_KEY, record);
  return record;
}

module.exports = {
  getTodayRecord,
  addScore
};
