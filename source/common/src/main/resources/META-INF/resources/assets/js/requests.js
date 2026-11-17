/** Keep mutations locked even when a snapshot updates their buttons. */
export class Requests {
  constructor(onChange = () => {}) {
    this.pending = new Set();
    this.onChange = onChange;
  }

  async run(key, task) {
    if (this.pending.has(key)) return;
    this.pending.add(key);
    this.onChange();
    try {
      return await task();
    } finally {
      this.pending.delete(key);
      this.onChange();
    }
  }

  restore(root = document) {
    for (const button of root.querySelectorAll('[data-request-disabled]')) {
      button.disabled = button.dataset.requestDisabled === 'true';
      delete button.dataset.requestDisabled;
    }
  }

  sync(root = document) {
    for (const button of root.querySelectorAll('[data-request-key]')) {
      if (this.pending.has(button.dataset.requestKey)) {
        if (!button.hasAttribute('data-request-disabled')) {
          button.dataset.requestDisabled = String(button.disabled);
        }
        button.disabled = true;
      } else if (button.hasAttribute('data-request-disabled')) {
        button.disabled = button.dataset.requestDisabled === 'true';
        delete button.dataset.requestDisabled;
      }
    }
  }
}
