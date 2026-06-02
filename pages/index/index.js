Page({
  data: {
    healthScore: 0,
    currentQuote: "深呼吸，感受当下的宁静。",
    quotes: [
      "深呼吸，感受当下的宁静。",
      "喝口水吧，让身体焕发活力。",
      "抬起头，看看远方的风景。",
      "你已经做得非常出色了，稍微休息一下吧。",
      "身体是革命的本钱，爱护自己从现在开始。",
      "每一分努力都值得被温柔对待。",
      "你的健康，是对家人最大的爱。"
    ],
    reminders: [],
    timerInterval: null
  },

  onLoad() {
    this.refreshQuote();
    this.initHealthScore();
  },

  onShow() {
    this.initReminders();
  },

  onHide() {
    if (this.data.timerInterval) {
      clearInterval(this.data.timerInterval);
    }
  },

  initHealthScore() {
    const score = wx.getStorageSync('healthScore') || 0;
    this.setData({ healthScore: score });
  },

  refreshQuote() {
    const index = Math.floor(Math.random() * this.data.quotes.length);
    this.setData({ currentQuote: this.data.quotes[index] });
  },

  initReminders() {
    const list = wx.getStorageSync('reminder_list') || [
      { id: '1', title: '起身活动', interval: 45, desc: '站起来伸个腰，走动2分钟' },
      { id: '2', title: '喝水提醒', interval: 60, desc: '给身体充个电，喝杯温水吧' },
      { id: '3', title: '远眺放松', interval: 30, desc: '凝视远处绿色或远方，放松睫状肌' }
    ];

    const now = Date.now();
    let reminders = list.map(item => {
      let nextTime = wx.getStorageSync(`next_${item.id}`);

      // 如果没有记录下次提醒时间，则初始化一个并保存
      if (!nextTime) {
        nextTime = now + item.interval * 60 * 1000;
        wx.setStorageSync(`next_${item.id}`, nextTime);
      }

      let remaining = Math.max(0, Math.floor((nextTime - now) / 1000));

      // 如果已经过期，则显示为0，等待用户点击完成
      if (remaining < 0) remaining = 0;

      return {
        ...item,
        remainingTime: remaining,
        remainingTimeStr: this.formatTime(remaining)
      };
    });

    this.setData({ reminders });
    this.startGlobalTimer();
  },

  startGlobalTimer() {
    if (this.data.timerInterval) clearInterval(this.data.timerInterval);
    
    this.setData({
      timerInterval: setInterval(() => {
        const now = Date.now();
        let reminders = this.data.reminders.map(item => {
          // 严格从本地存储读取 established 的时间锚点
          const nextTime = wx.getStorageSync(`next_${item.id}`);
          
          if (!nextTime) return item; // 理论上不会发生，因为 initReminders 已初始化

          let remaining = Math.max(0, Math.floor((nextTime - now) / 1000));
          
          // 如果刚刚到达 0 秒，触发通知
          if (remaining === 0 && item.remainingTime > 0) {
            this.triggerNotify(item);
          }
          
          return {
            ...item,
            remainingTime: remaining,
            remainingTimeStr: this.formatTime(remaining)
          };
        });
        this.setData({ reminders });
      }, 1000)
    });
  },

  formatTime(seconds) {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  },

  triggerNotify(item) {
    const settings = wx.getStorageSync('settings') || { 
      dnd: true, 
      sound: true,
      startTime: '09:00',
      endTime: '17:00'
    };
    
    const now = new Date();
    const currentStr = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;

    // 检查是否在生效时间内
    if (currentStr < settings.startTime || currentStr > settings.endTime) {
      console.log('不在提醒时间内:', currentStr);
      // 仍然重置计时器
      const nextTime = Date.now() + item.interval * 60 * 1000;
      wx.setStorageSync(`next_${item.id}`, nextTime);
      this.initReminders();
      return;
    }
    
    if (settings.dnd) {
      const hour = now.getHours();
      if (hour >= 22 || hour < 8) return;
    }

    wx.vibrateLong();
    wx.showModal({
      title: item.title,
      content: item.desc || '到时间啦！',
      confirmText: '去完成',
      cancelText: '稍后',
      success: (res) => {
        if (res.confirm) {
          // 仅关闭弹窗，不自动完成。用户需要手动点击卡片上的“已完成”
          console.log('用户选择去完成任务');
        }
      }
    });
  },

  completeTask(e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.reminders.find(r => r.id === id);
    if (!item || item.remainingTime > 0) return;

    const settings = wx.getStorageSync('settings') || { subscribe: false };
    
    // 如果开启了系统通知，尝试请求订阅
    if (settings.subscribe) {
      this.requestSubscription(item).then(() => {
        this.finalizeTask(id, item);
      }).catch(() => {
        this.finalizeTask(id, item);
      });
    } else {
      this.finalizeTask(id, item);
    }
  },

  requestSubscription(item) {
    return new Promise((resolve, reject) => {
      // 这里的模板 ID 需要在微信公众后台申请，此处为演示占位符
      const templateId = 'YOUR_HEALTH_TEMPLATE_ID'; 
      
      wx.requestSubscribeMessage({
        tmplIds: [templateId],
        success: (res) => {
          if (res[templateId] === 'accept') {
            console.log('订阅成功');
            // 在实际生产中，这里应调用云函数注册一个定时任务
          }
          resolve();
        },
        fail: (err) => {
          console.error('订阅请求失败', err);
          resolve(); // 失败也允许完成任务，提升体验
        }
      });
    });
  },

  finalizeTask(id, item) {
    const nextTime = Date.now() + item.interval * 60 * 1000;
    wx.setStorageSync(`next_${id}`, nextTime);

    const newScore = this.data.healthScore + 10;
    this.setData({ healthScore: newScore });
    wx.setStorageSync('healthScore', newScore);

    wx.showToast({ title: '太棒了！+10分', icon: 'success' });
    this.initReminders();
  },

  startBreathing() {
    wx.navigateTo({ url: '/pages/breathing/breathing' });
  },

  onShareAppMessage() {
    return {
      title: `我今天的健康分已经 ${this.data.healthScore} 啦！快来一起工作并爱护身体吧~`,
      path: '/pages/index/index',
      imageUrl: '/images/share-cover.png' // 建议后续添加一张精美封面图
    }
  },

  onShareTimeline() {
    return {
      title: `【健康提醒】“${this.data.currentQuote}” —— 守护每一位奋斗者。`,
      query: `score=${this.data.healthScore}`
    }
  }
})
