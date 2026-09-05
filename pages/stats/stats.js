const health = require('../../utils/health.js');

const BAR_MAX_HEIGHT = 180;

Page({
  data: {
    week: [],
    weekTotal: 0,
    activeDays: 0,
    total: 0,
    streak: 0,
    todayScore: 0
  },

  onLoad() {
    this.refresh();
  },

  onShow() {
    this.refresh();
  },

  refresh() {
    const record = health.getTodayRecord();
    const week = health.getWeekHistory(BAR_MAX_HEIGHT);
    const weekTotal = week.reduce((sum, d) => sum + d.score, 0);
    const activeDays = week.filter(d => d.done > 0).length;

    this.setData({
      week,
      weekTotal,
      activeDays,
      total: record.total,
      streak: record.streak,
      todayScore: record.score
    });
  }
})
