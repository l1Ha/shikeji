App({
  onLaunch() {
    // 记录启动日志，仅保留最近 50 条避免无限增长
    const logs = wx.getStorageSync('logs') || []
    logs.unshift(Date.now())
    wx.setStorageSync('logs', logs.slice(0, 50))
  },
  globalData: {
    userInfo: null
  }
})
