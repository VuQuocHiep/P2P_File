export const $ = (selector) => document.querySelector(selector);

const previousHTML = new WeakMap();
const keyOf = (node) => node.nodeType === 1 && (node.id || node.getAttribute('data-key'));
const compatible = (current, next) =>
  current.nodeType === next.nodeType && current.nodeName === next.nodeName;

/** Patch existing nodes so live data does not replace focused controls or open details. */
export function updateHTML(root, html, reset = false) {
  if (!reset && previousHTML.get(root) === html) return;
  const active = document.activeElement;
  const template = document.createElement('template');
  template.innerHTML = html;
  if (reset) root.replaceChildren(template.content);
  else patchChildren(root, template.content);
  if (!reset && active?.isConnected && root.contains(active) && document.activeElement !== active) {
    active.focus({ preventScroll: true });
  }
  previousHTML.set(root, html);
}

function patchChildren(current, next) {
  const oldNodes = [...current.childNodes];
  const keyed = new Map(oldNodes.filter(keyOf).map((node) => [keyOf(node), node]));
  const used = new Set();
  let cursor = current.firstChild;
  for (const desired of [...next.childNodes]) {
    const key = keyOf(desired);
    let existing = key ? keyed.get(key) : cursor;
    if (
      existing &&
      (used.has(existing) || (!key && keyOf(existing)) || !compatible(existing, desired))
    ) {
      existing = null;
    }
    if (!existing) {
      current.insertBefore(desired, cursor);
      used.add(desired);
      continue;
    }
    if (existing !== cursor) current.insertBefore(existing, cursor);
    patchNode(existing, desired);
    used.add(existing);
    cursor = existing.nextSibling;
  }
  for (const node of oldNodes) {
    if (!used.has(node)) node.remove();
  }
}

function patchNode(current, next) {
  if (current.nodeType !== 1) {
    if (current.nodeValue !== next.nodeValue) current.nodeValue = next.nodeValue;
    return;
  }
  const focused = current === document.activeElement;
  const selected = current.tagName === 'SELECT' ? (focused ? current.value : next.value) : null;
  for (const attribute of [...current.attributes]) {
    if (current.tagName === 'DETAILS' && attribute.name === 'open') continue;
    if (attribute.name === 'data-request-disabled') continue;
    if (!next.hasAttribute(attribute.name)) current.removeAttribute(attribute.name);
  }
  for (const attribute of [...next.attributes]) {
    if (current.tagName === 'DETAILS' && attribute.name === 'open') continue;
    if (focused && attribute.name === 'value') continue;
    if (current.getAttribute(attribute.name) !== attribute.value) {
      current.setAttribute(attribute.name, attribute.value);
    }
  }
  patchChildren(current, next);
  if (selected !== null && [...current.options].some((option) => option.value === selected)) {
    current.value = selected;
  }
}
