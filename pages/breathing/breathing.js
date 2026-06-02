Page({
  data: {
    status: 'idle', // idle, inhale, hold, exhale
    statusText: '准备好了吗？',
    timer: 60,
    timerStr: '01:00',
    isRunning: false,
    interval: null,
    breathInterval: null
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
    clearTimeout(this.data.breathInterval);
    this.setData({ 
      isRunning: false, 
      status: 'idle', 
      statusText: '太棒了，感受一下身体的变化',
      timerStr: '01:00'
    });
  },

  startTimer() {
    const interval = setInterval(() => {
      let t = this.data.timer - 1;
      if (t <= 0) {
        this.stop();
        return;
      }
      this.setData({ 
        timer: t,
        timerStr: `00:${t.toString().padStart(2, '0')}`
      });
    }, 1000);
    this.setData({ interval });
  },

  breathCycle() {
    if (!this.data.isRunning) return;

    // 吸气 4s
    this.setData({ status: 'inhale', statusText: '吸气...' });
    
    this.data.breathInterval = setTimeout(() => {
      if (!this.data.isRunning) return;
      // 呼气 4s
      this.setData({ status: 'exhale', statusText: '呼气...' });
      
      this.data.breathInterval = setTimeout(() => {
        this.breathCycle();
      }, 4000);
    }, 4000);
  },

  onUnload() {
    this.stop();
  }
})
