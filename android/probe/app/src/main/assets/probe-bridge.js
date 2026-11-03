(() => {
  'use strict';
  if (window !== window.top) return;
  const config = __PROBE_CONFIG__;
  const limit = 2 * 1024 * 1024;
  const encoder = new TextEncoder();
  let sentBytes = 0;
  const allowed = raw => {
    try {
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' && !u.username && !u.password && (!u.port || u.port === '443') &&
        (config.selfCheck ? u.hostname === 'appassets.androidplatform.net' && u.pathname.startsWith('/probe/') :
          u.hostname === 'sale.591.com.tw' && /\/(?:search|house|sale|list)(?:\/|$|_)/.test(u.pathname));
    } catch (_) { return false; }
  };
  const network = (url, startedAt, entryType) => {
    if (config.selfCheck || (navigator.serviceWorker && navigator.serviceWorker.controller)) return false;
    const entries = performance.getEntriesByName(url);
    const entry = entries[entries.length - 1];
    // A validated cache hit transfers headers only; ambiguous timing never proves a fresh body.
    return !!entry && entry.entryType === entryType && entry.startTime >= startedAt && !entry.workerStart && entry.encodedBodySize > 0 &&
      entry.transferSize === entry.encodedBodySize + 300 && entry.decodedBodySize > 0;
  };
  const envelope = (kind, url, status, contentType, body, startedAt = 0) => ({kind, url, status, contentType, body,
    documentUrl: location.href, navigationId: config.navigationId, networkObserved: network(url, startedAt, kind === 'json' ? 'resource' : 'navigation')});
  const send = (url, status, contentType, body, startedAt) => {
    if (!allowed(url) || status < 200 || status >= 300 || !/application\/(?:[\w.+-]*\+)?json\b/i.test(contentType || '')) return;
    const message = JSON.stringify(envelope('json', url, status, contentType, body, startedAt));
    const size = encoder.encode(message).length;
    if (size > limit || sentBytes + size > 8 * 1024 * 1024) return;
    sentBytes += size;
    if (window.HouseLensBridge) window.HouseLensBridge.postMessage(message);
  };
  const observe = async (response, startedAt) => {
    if (!allowed(response.url) || response.status < 200 || response.status >= 300 ||
        !/application\/(?:[\w.+-]*\+)?json\b/i.test(response.headers.get('content-type') || '')) return;
    const reader = response.clone().body?.getReader();
    if (!reader) return;
    const decoder = new TextDecoder();
    let bytes = 0, body = '';
    try {
      while (true) {
        const part = await reader.read();
        if (part.done) break;
        bytes += part.value.byteLength;
        if (bytes > limit) { await reader.cancel(); return; }
        body += decoder.decode(part.value, {stream:true});
      }
      body += decoder.decode();
      send(response.url, response.status, response.headers.get('content-type'), body, startedAt);
    } finally { reader.releaseLock(); }
  };
  const originalFetch = window.fetch;
  window.fetch = function (...args) {
    const startedAt = performance.now();
    return originalFetch.apply(this, args).then(response => {
      observe(response, startedAt).catch(() => {});
      return response;
    });
  };
  const originalSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function (...args) {
    const startedAt = performance.now();
    this.addEventListener('load', () => {
      try {
        if (!allowed(this.responseURL)) return;
        const body = this.responseType === 'json' ? JSON.stringify(this.response) :
          (!this.responseType || this.responseType === 'text') ? this.responseText : null;
        if (typeof body === 'string' && body.length <= limit)
          send(this.responseURL, this.status, this.getResponseHeader('content-type'), body, startedAt);
      } catch (_) { /* Unreadable responses remain missing evidence. */ }
    }, {once:true});
    return originalSend.apply(this, args);
  };
  window.__houseLensSnapshot = () => {
    const body = document.documentElement?.outerHTML || '';
    const result = envelope('dom', location.href, null, 'text/html', body);
    result.ready = document.readyState === 'complete';
    const message = JSON.stringify(result);
    return encoder.encode(message).length <= limit ? message :
      JSON.stringify(envelope('oversize', location.href, null, '', ''));
  };
})();
