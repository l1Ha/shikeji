const health = require('../../utils/health.js');

Page({
  data: {
    modes: [
      { name: '4-7-8 助眠', inhale: 4000, hold: 7000, exhale: 8000, desc: '深层放松，缓解焦虑' },
      { name: '等比呼吸 (方块)', inhale: 4000, hold: 4000, exhale: 4000, desc: '提升专注，平衡情绪' },
      { name: '快速冷静', inhale: 2000, hold: 0, exhale: 4000, desc: '迅速平复激动心情' }
    ],
    currentModeIndex: 1,
    status: 'idle',
    statusText: '准备好了吗？',
    phaseCount: 0,
    timer: 60,
    timerStr: '01:00',
    isRunning: false,
    interval: null,
    breathTimeout: null,
    phaseEnd: 0,
    phaseDuration: 4000
  },

  selectMode(e) {
    this.setData({ currentModeIndex: e.currentTarget.dataset.index });
  },

  toggleBreathing() {
    if (this.data.isRunning) {
      this.stop();
    } else {
      this.start();
    }
  },

  start() {
    this.setData({ isRunning: true, timer: 60 });
    this.startTimer();
    this.breathCycle();
  },

  stop() {
    clearInterval(this.data.interval);
    clearTimeout(this.data.breathTimeout);
    const finished = this.data.timer <= 0;
    this.setData({
      isRunning: false,
      status: 'idle',
      statusText: finished ? '完成！身体感谢你 🎉' : '太棒了，感受一下身体的变化',
      phaseCount: 0,
      timerStr: '01:00',
      interval: null,
      breathTimeout: null
    });
    if (finished) {
      this.awardBonus();
    }
  },

  // 完成一整节呼吸练习，奖励 5 点健康分
  awardBonus() {
    const record = health.addScore(5);
    wx.showToast({ title: `呼吸完成 +${5}分`, icon: 'success' });
    this.setData({ earnedScore: record.score });
  },

  startTimer() {
    const interval = setInterval(() => {
      let t = this.data.timer - 1;
      if (t <= 0) {
        this.setData({ timer: 0 });
        this.stop();
        return;
      }
      this.setData({
        timer: t,
        timerStr: `00:${t.toString().padStart(2, '0')}`,
        phaseCount: this.currentPhaseCount()
      });
    }, 1000);
    this.setData({ interval });
  },

  // 当前呼吸阶段剩余秒数
  currentPhaseCount() {
    if (!this.data.phaseEnd) return 0;
    return Math.max(0, Math.ceil((this.data.phaseEnd - Date.now()) / 1000));
  },

  setPhase(status, text, duration) {
    this.setData({
      status,
      statusText: text,
      phaseEnd: Date.now() + duration,
      phaseDuration: duration,
      phaseCount: Math.ceil(duration / 1000)
    });
  },

  breathCycle() {
    if (!this.data.isRunning) return;
    const mode = this.data.modes[this.data.currentModeIndex];

    // 1. 吸气
    this.setPhase('inhale', '吸气...', mode.inhale);

    this.data.breathTimeout = setTimeout(() => {
      if (!this.data.isRunning) return;

      // 2. 屏息 (如果有)
      if (mode.hold > 0) {
        this.setPhase('hold', '屏息...', mode.hold);
        this.data.breathTimeout = setTimeout(() => {
          this.executeExhale(mode);
        }, mode.hold);
      } else {
        this.executeExhale(mode);
      }
    }, mode.inhale);
  },

  executeExhale(mode) {
    if (!this.data.isRunning) return;
    this.setPhase('exhale', '呼气...', mode.exhale);
    this.data.breathTimeout = setTimeout(() => {
      this.breathCycle();
    }, mode.exhale);
  },

  onUnload() {
    clearInterval(this.data.interval);
    clearTimeout(this.data.breathTimeout);
  }
})
