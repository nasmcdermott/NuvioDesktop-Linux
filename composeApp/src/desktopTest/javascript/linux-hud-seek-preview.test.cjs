// Runs the real seek-preview card block from controls.js against fake elements and a fake clock.
// No browser: this checks the position math, the native-frame request and the keyboard auto-hide.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
const source = fs.readFileSync(path.join(__dirname, '../../desktopMain/resources/player-ui/controls.js'), 'utf8');
const start = source.indexOf('const KEYBOARD_SEEK_THUMBNAIL_HIDE_MS');
const end = source.indexOf('window.nuvioSeekThumbnailReady');
assert.ok(start >= 0 && end > start, 'seek-preview block must exist');
const block = source.slice(start, end);

function fakeClock() {
  let now = 0;
  let nextId = 1;
  const timers = [];
  return {
    setTimeout(fn, ms) {
      const id = nextId++;
      timers.push({ id, fn, due: now + ms });
      return id;
    },
    clearTimeout(id) {
      const index = timers.findIndex(timer => timer.id === id);
      if (index >= 0) timers.splice(index, 1);
    },
    advance(ms) {
      const target = now + ms;
      for (;;) {
        let next = -1;
        timers.forEach((timer, index) => {
          if (timer.due <= target && (next < 0 || timer.due < timers[next].due)) next = index;
        });
        if (next < 0) break;
        const [due] = timers.splice(next, 1);
        now = due.due;
        due.fn();
      }
      now = target;
    },
  };
}

const element = (left = 0, width = 400) => {
  const classes = new Set();
  return {
    hidden: true,
    textContent: '',
    style: {},
    removed: false,
    classList: {
      add: name => classes.add(name),
      remove: name => classes.delete(name),
      toggle: (name, on) => { if (on) classes.add(name); else classes.delete(name); },
      contains: name => classes.has(name),
    },
    getBoundingClientRect: () => ({ left, width }),
    removeAttribute: () => {},
  };
};

function load({ liveWindow = null, previews = true, durationMs = 100000, positionMs = 40000 } = {}) {
  const clock = fakeClock();
  const sent = [];
  const seek = element(0, 400);
  const timeline = element(0, 400);
  const thumbnail = element();
  const image = element();
  const time = element();
  const chapter = element();
  const context = {
    state: { durationMs, positionMs, seekThumbnailsEnabled: previews, seekStepSeconds: 10 },
    seek,
    timeline,
    seekThumbnail: thumbnail,
    seekThumbnailImage: image,
    seekThumbnailTime: time,
    seekThumbnailChapter: chapter,
    seekThumbnailCache: new Map(),
    seekThumbnailRequestTimer: 0,
    pendingSeekThumbnailPosition: -1,
    isScrubbing: false,
    scrubPositionMs: 0,
    window: { setTimeout: clock.setTimeout, clearTimeout: clock.clearTimeout },
    iptvLiveWindow: () => liveWindow,
    displayChapters: () => [],
    formatTime: ms => {
      const total = Math.max(0, Math.round(ms / 1000));
      return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
    },
    SEEK_THUMBNAIL_SETTLE_MS: 220,
    seekrCueAt: () => null,
    drawSeekrCue: () => {},
    send: (type, value) => sent.push([type, value]),
    seekStepSeconds: () => 10,
  };
  vm.createContext(context);
  vm.runInContext(block + '\nglobalThis.__preview = { showKeyboardSeekThumbnail, showSeekThumbnailAt };', context);
  return { context, clock, sent, seek, thumbnail, image, time, chapter, preview: context.__preview };
}

test('a forward step previews the target position and requests that frame', () => {
  const h = load();
  h.preview.showKeyboardSeekThumbnail(1);
  assert.equal(h.thumbnail.hidden, false);
  assert.equal(h.time.textContent, '00:50');
  assert.equal(h.thumbnail.classList.contains('no-preview'), false);
  h.clock.advance(220); // the existing settle delay before asking the native decoder
  assert.deepEqual(h.sent, [['seekThumbnail', 50000]]);
});

test('a backward step previews the target position', () => {
  const h = load({ positionMs: 40000 });
  h.preview.showKeyboardSeekThumbnail(-1);
  assert.equal(h.time.textContent, '00:30');
});

test('the target is clamped to the start and end of the title', () => {
  const atStart = load({ positionMs: 4000 });
  atStart.preview.showKeyboardSeekThumbnail(-1);
  assert.equal(atStart.time.textContent, '00:00');
  const atEnd = load({ positionMs: 96000 });
  atEnd.preview.showKeyboardSeekThumbnail(1);
  assert.equal(atEnd.time.textContent, '01:40');
});

test('the keyboard card fades after its own delay', () => {
  const h = load();
  h.preview.showKeyboardSeekThumbnail(1);
  h.clock.advance(1399);
  assert.equal(h.thumbnail.hidden, false);
  h.clock.advance(1);
  assert.equal(h.thumbnail.hidden, true);
});

test('live TV keeps its pointer-only behind-live card, with no frame request', () => {
  const h = load({ liveWindow: { start: 0, end: 100000 } });
  h.preview.showKeyboardSeekThumbnail(1);
  h.clock.advance(1000);
  assert.equal(h.thumbnail.hidden, true);
  assert.deepEqual(h.sent, []);
});

test('previews off still shows the time and chapter card without a frame request', () => {
  const h = load({ previews: false });
  h.preview.showKeyboardSeekThumbnail(1);
  assert.equal(h.thumbnail.hidden, false);
  assert.equal(h.thumbnail.classList.contains('no-preview'), true);
  h.clock.advance(220);
  assert.deepEqual(h.sent, []);
});
