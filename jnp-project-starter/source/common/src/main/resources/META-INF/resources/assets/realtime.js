'use strict';

/** A full snapshot after reconnect repairs any events missed while the browser was offline. */
window.LiveChannel = class {
  constructor(path, onSnapshot, onState, onUnauthorized = () => {}) {
    this.path = path;
    this.onSnapshot = onSnapshot;
    this.onState = onState;
    this.onUnauthorized = onUnauthorized;
    this.wanted = false;
    this.connected = false;
    this.retryMs = 1000;
  }

  start() {
    if (this.wanted) return;
    this.wanted = true;
    this.connect();
    this.watchdog = setInterval(() => {
      if (this.connected && Date.now() - this.lastFrame > 15000) this.socket?.close();
    }, 5000);
  }

  connect() {
    if (!this.wanted) return;
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(protocol + '//' + location.host + this.path);
    this.socket = socket;
    socket.onopen = () => {
      if (this.socket !== socket) return;
      this.connected = true;
      this.retryMs = 1000;
      this.lastFrame = Date.now();
      this.onState(true);
    };
    socket.onmessage = event => {
      if (this.socket !== socket) return;
      try {
        const message = JSON.parse(event.data);
        if (message.type === 'snapshot') {
          this.lastFrame = Date.now();
          this.onSnapshot(message.data, message);
        }
      } catch (error) {
        console.error('Không đọc được WebSocket snapshot:', error);
      }
    };
    socket.onerror = () => socket.close();
    socket.onclose = event => {
      if (this.socket !== socket) return;
      this.connected = false;
      this.onState(false);
      if (!this.wanted) return;
      if (event.code === 1008) { this.stop(); this.onUnauthorized(); return; }
      const delay = this.retryMs + Math.floor(Math.random() * 300);
      this.retryMs = Math.min(15000, this.retryMs * 2);
      this.reconnect = setTimeout(() => this.connect(), delay);
    };
  }

  refresh() {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({type: 'refresh'}));
      return true;
    }
    return false;
  }

  stop() {
    this.wanted = false;
    this.connected = false;
    clearTimeout(this.reconnect);
    clearInterval(this.watchdog);
    const socket = this.socket;
    this.socket = null;
    socket?.close();
  }
};
