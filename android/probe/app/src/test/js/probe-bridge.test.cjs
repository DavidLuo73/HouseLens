const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = readFileSync('app/src/main/assets/probe-bridge.js', 'utf8');
const messages = [];
let resolveFetch;
class XHR {
  open(method, url) { this.url = url; }
  addEventListener(kind, listener) { this.listener = listener; }
  send() { this.responseURL = this.url; this.status = 200; this.responseText = '{"xhr":true}'; this.response = {xhr:true}; this.listener(); }
  getResponseHeader() { return 'application/json'; }
}
const context = {
  URL, TextEncoder, TextDecoder, JSON, XMLHttpRequest: XHR,
  location: {href:'https://sale.591.com.tw/?section=38'},
  navigator: {}, performance: {now: () => 10, getEntriesByName: () => []},
  document: {readyState:'complete', documentElement:{outerHTML:'<html>ready</html>'}},
  HouseLensBridge: {postMessage: message => messages.push(JSON.parse(message))},
  fetch: () => new Promise(resolve => { resolveFetch = resolve; }),
};
context.window = context; context.top = context;
vm.createContext(context);
vm.runInContext(source.replace('__PROBE_CONFIG__', JSON.stringify({navigationId:7, selfCheck:false})), context);
(async () => {
  const original = {url:'https://sale.591.com.tw/api/search', status:200, headers:{get:()=> 'application/json'}, clone:()=>({body:new ReadableStream({start(c){c.enqueue(new TextEncoder().encode('{"house_list":[]}'));c.close();}})})};
  const pending = context.fetch('/api/search');
  context.navigationId = 99; // A late response must keep the ID captured at document start.
  resolveFetch(original);
  assert.equal(await pending, original);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(messages[0].navigationId, 7);
  assert.equal(messages[0].networkObserved, false);
  assert.equal(messages[0].body, '{"house_list":[]}');
  const xhr = new context.XMLHttpRequest(); xhr.open('GET', 'https://sale.591.com.tw/api/list'); xhr.send();
  assert.equal(messages[1].body, '{"xhr":true}');
  const evil = new context.XMLHttpRequest(); evil.open('GET', 'https://sale.591.com.tw.evil.test/api/list'); evil.send();
  assert.equal(messages.length, 2);
  const snapshot = JSON.parse(context.__houseLensSnapshot());
  assert.equal(snapshot.navigationId, 7);
  assert.equal(snapshot.networkObserved, false);
  assert.equal(snapshot.body, '<html>ready</html>');
  context.performance.getEntriesByName = () => [{entryType:'resource', startTime:1, encodedBodySize:50, decodedBodySize:50, transferSize:350}];
  const cached = new context.XMLHttpRequest(); cached.open('GET', 'https://sale.591.com.tw/api/search'); cached.send();
  assert.equal(messages[2].networkObserved, false, 'old live timing must not certify a new cached response');
  assert.equal(JSON.parse(context.__houseLensSnapshot()).networkObserved, false, 'a resource timing cannot certify document transfer');
  const jsonXhr = new context.XMLHttpRequest(); jsonXhr.open('GET', 'https://sale.591.com.tw/api/list'); jsonXhr.responseType = 'json'; jsonXhr.send();
  assert.equal(messages.length, 4, 'XHR JSON responseType must be captured');
  context.performance.getEntriesByName = () => [{entryType:'resource', startTime:11, encodedBodySize:50, decodedBodySize:50, transferSize:350}];
  const live = new context.XMLHttpRequest(); live.open('GET', 'https://sale.591.com.tw/api/list'); live.send();
  assert.equal(messages[4].networkObserved, true);
  context.navigator.serviceWorker = {controller:{}};
  const worker = new context.XMLHttpRequest(); worker.open('GET', 'https://sale.591.com.tw/api/list'); worker.send();
  assert.equal(messages[5].networkObserved, false);
  context.document.documentElement.outerHTML = 'x'.repeat(2097153);
  assert.equal(JSON.parse(context.__houseLensSnapshot()).kind, 'oversize');
  const local = {...context, location: {href: 'https://appassets.androidplatform.net/assets/selfcheck.html'},
    document: {readyState:'complete', documentElement:{outerHTML:'<html></html>', dataset:{}}}};
  local.window = local; local.top = local;
  vm.createContext(local);
  vm.runInContext(source.replace('__PROBE_CONFIG__', JSON.stringify({navigationId:2, selfCheck:true})), local);
  assert.equal(JSON.parse(local.__houseLensSnapshot()).ready, false, 'local checks must finish before capture');
  local.document.documentElement.dataset.selfcheckReady = 'true';
  assert.equal(JSON.parse(local.__houseLensSnapshot()).ready, true);
  console.log('probe-bridge: fetch, XHR, stale ID, exact host, cache evidence, size and selfcheck readiness passed');
})().catch(e => { console.error(e); process.exitCode = 1; });
