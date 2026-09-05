Page({
  data: {
    reminders: [],
    dnd: true,
    sound: true,
    subscribe: false,
    startTime: '09:00',
    endTime: '17:00'
  },

  onLoad() {
    const settings = wx.getStorageSync('settings') || {
      dnd: true,
      sound: true,
      subscribe: false,
      startTime: '09:00',
      endTime: '17:00'
    };
    const reminders = wx.getStorageSync('reminder_list') || [
      { id: '1', title: '起身活动', interval: 45, desc: '站起来伸个腰，走动2分钟' },
      { id: '2', title: '喝水提醒', interval: 60, desc: '给身体充个电，喝杯温水吧' },
      { id: '3', title: '远眺放松', interval: 30, desc: '凝视远处绿色或远方，放松睫状肌' }
    ];
    this.setData({ ...settings, reminders });
  },

  onSubscribeChange(e) {
    const isEnabled = e.detail.value;
    if (isEnabled) {
      wx.showModal({
        title: '开启系统通知',
        content: '开启后需在完成任务时授权订阅消息，以便在后台接收提醒。',
        confirmText: '明确',
        showCancel: false
      });
    }
    this.setData({ subscribe: isEnabled });
  },

  onStartTimeChange(e) {
    this.setData({ startTime: e.detail.value });
  },

  onEndTimeChange(e) {
    this.setData({ endTime: e.detail.value });
  },

  onTitleInput(e) {
    const { id } = e.currentTarget.dataset;
    const { value } = e.detail;
    const reminders = this.data.reminders.map(item =>
      item.id === id ? { ...item, title: value } : item
    );
    this.setData({ reminders });
  },

  onDescInput(e) {
    const { id } = e.currentTarget.dataset;
    const { value } = e.detail;
    const reminders = this.data.reminders.map(item =>
      item.id === id ? { ...item, desc: value } : item
    );
    this.setData({ reminders });
  },

  onIntervalChange(e) {
    const { id } = e.currentTarget.dataset;
    const { value } = e.detail;
    const reminders = this.data.reminders.map(item =>
      item.id === id ? { ...item, interval: value } : item
    );
    this.setData({ reminders });
  },

  addReminder() {
    const newId = Date.now().toString();
    const reminders = [...this.data.reminders, {
      id: newId,
      title: '新提醒',
      interval: 30,
      desc: '请设置您的提醒内容'
    }];
    this.setData({ reminders });
  },

  deleteReminder(e) {
    const { id } = e.currentTarget.dataset;
    if (this.data.reminders.length <= 1) {
      wx.showToast({ title: '至少保留一个提醒', icon: 'none' });
      return;
    }
    const reminders = this.data.reminders.filter(item => item.id !== id);
    this.setData({ reminders });
  },

  onDndChange(e) {
    this.setData({ dnd: e.detail.value });
  },

  onSoundChange(e) {
    this.setData({ sound: e.detail.value });
  },

  saveAll() {
    // 过滤空白标题，避免出现无名提醒
    const reminders = this.data.reminders
      .map(item => ({ ...item, title: (item.title || '').trim() || '未命名提醒' }))
      .map(item => ({ ...item, interval: Math.max(1, parseInt(item.interval, 10) || 1) }));

    wx.setStorageSync('settings', {
      dnd: this.data.dnd,
      sound: this.data.sound,
      subscribe: this.data.subscribe,
      startTime: this.data.startTime,
      endTime: this.data.endTime
    });

    // 清理已删除提醒的计时锚点，避免残留数据
    const keptIds = reminders.map(item => item.id);
    const prevList = wx.getStorageSync('reminder_list') || [];
    prevList.forEach(item => {
      if (keptIds.indexOf(item.id) === -1) {
        wx.removeStorageSync(`next_${item.id}`);
      }
    });

    // 保留已有提醒的时间锚点；间隔有调整时按新间隔重新计时
    reminders.forEach(item => {
      const prev = prevList.find(p => p.id === item.id);
      if (!prev) {
        wx.setStorageSync(`next_${item.id}`, Date.now() + item.interval * 60 * 1000);
      } else if (prev.interval !== item.interval) {
        wx.setStorageSync(`next_${item.id}`, Date.now() + item.interval * 60 * 1000);
      }
    });

    wx.setStorageSync('reminder_list', reminders);

    wx.showToast({
      title: '设置已应用',
      icon: 'success'
    });

    setTimeout(() => {
      wx.switchTab({ url: '/pages/index/index' });
    }, 1500);
  },

  onShareAppMessage() {
    return {
      title: '这个健康提醒小程序真好用，还能自定义时间，推荐给你！',
      path: '/pages/index/index'
    }
  }
})
