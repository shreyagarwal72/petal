/**
 * Petal website — Material 3 Expressive behaviour
 * 1. Shape engine: 35 shapes sampled as radius-per-angle, so any two shapes morph
 *    into each other with a spring (overshoot included).
 * 2. Theme engine: palette, dark, AMOLED, with a circular reveal transition.
 * 3. Phone demo, wavy player, architecture tabs, menu, snackbar, ripple, reveal.
 */
(function () {
  'use strict';

  var root = document.documentElement;
  var reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
  var $ = function (s, c) { return (c || document).querySelector(s); };
  var $$ = function (s, c) { return Array.prototype.slice.call((c || document).querySelectorAll(s)); };

  /* ======================================================================
     1. SHAPE ENGINE
     ====================================================================== */
  var N = 144;                        // samples around the shape
  var TAU = Math.PI * 2;
  var START = -Math.PI / 2;           // first sample points straight up

  function ngon(n, rotDeg) {
    var out = [], rot = (rotDeg === undefined ? -90 : rotDeg) * Math.PI / 180;
    for (var i = 0; i < n; i++) { var a = rot + TAU * i / n; out.push([Math.cos(a), Math.sin(a)]); }
    return out;
  }
  function star(n, inner, rotDeg) {
    var out = [], rot = (rotDeg === undefined ? -90 : rotDeg) * Math.PI / 180;
    for (var i = 0; i < n * 2; i++) { var a = rot + Math.PI * i / n, r = i % 2 ? inner : 1; out.push([r * Math.cos(a), r * Math.sin(a)]); }
    return out;
  }
  function polar(fn, steps) {
    var out = [], n = steps || 360;
    for (var i = 0; i < n; i++) { var a = TAU * i / n, r = fn(a); out.push([r * Math.cos(a), r * Math.sin(a)]); }
    return out;
  }
  function arc(cx, cy, r, fromDeg, toDeg, steps) {
    var out = [], n = steps || 48;
    for (var i = 0; i <= n; i++) { var a = (fromDeg + (toDeg - fromDeg) * i / n) * Math.PI / 180; out.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]); }
    return out;
  }
  function superellipse(a, b, p) {
    return polar(function (t) {
      var c = Math.abs(Math.cos(t)), s = Math.abs(Math.sin(t));
      return 1 / Math.pow(Math.pow(c / a, p) + Math.pow(s / b, p), 1 / p);
    }, 240);
  }

  // [id, name, polygon builder, corner softening (in samples)]
  var DEFS = [
    ['circle', 'Circle', function () { return polar(function () { return 1; }, 120); }, 0],
    ['square', 'Square', function () { return superellipse(1, 1, 5); }, 0],
    ['slanted', 'Slanted', function () { return [[-.55, -.8], [1, -.8], [.55, .8], [-1, .8]]; }, 5],
    ['arch', 'Arch', function () { return arc(0, -.15, .85, 180, 360).concat([[.85, 1], [-.85, 1]]); }, 3],
    ['semicircle', 'SemiCircle', function () { return arc(0, 0, 1, 180, 360, 60); }, 3],
    ['oval', 'Oval', function () { return polar(function (t) { var a = .72, b = 1; return a * b / Math.sqrt(Math.pow(b * Math.cos(t), 2) + Math.pow(a * Math.sin(t), 2)); }, 180); }, 0],
    ['pill', 'Pill', function () { return arc(0, -.42, .55, 180, 360).concat(arc(0, .42, .55, 0, 180)); }, 0],
    ['triangle', 'Triangle', function () { return ngon(3); }, 9],
    ['arrow', 'Arrow', function () { return [[0, -1], [1, 0], [.45, 0], [.45, 1], [-.45, 1], [-.45, 0], [-1, 0]]; }, 5],
    ['fan', 'Fan', function () { return [[0, .95]].concat(arc(0, .95, 1.75, -145, -35)); }, 4],
    ['diamond', 'Diamond', function () { return ngon(4); }, 5],
    ['clamshell', 'ClamShell', function () { return polar(function (t) { return 1 + .07 * Math.cos(8 * t); }, 360).filter(function (p) { return p[1] <= .15; }).concat([[1, .35], [-1, .35]]).sort(function (a, b) { return Math.atan2(a[1] - .1, a[0]) - Math.atan2(b[1] - .1, b[0]); }); }, 2],
    ['pentagon', 'Pentagon', function () { return ngon(5); }, 6],
    ['gem', 'Gem', function () { return [[-.5, -.7], [.5, -.7], [1, -.1], [0, 1], [-1, -.1]]; }, 3],
    ['sunny', 'Sunny', function () { return star(8, .8); }, 4],
    ['verysunny', 'VerySunny', function () { return star(12, .84); }, 3],
    ['cookie4', 'Cookie4Sided', function () { return polar(function (t) { return .86 + .14 * Math.cos(4 * t + Math.PI); }); }, 0],
    ['cookie6', 'Cookie6Sided', function () { return polar(function (t) { return .88 + .12 * Math.cos(6 * t); }); }, 0],
    ['cookie7', 'Cookie7Sided', function () { return polar(function (t) { return .89 + .11 * Math.cos(7 * t); }); }, 0],
    ['cookie9', 'Cookie9Sided', function () { return polar(function (t) { return .91 + .09 * Math.cos(9 * t); }); }, 0],
    ['cookie12', 'Cookie12Sided', function () { return polar(function (t) { return .93 + .07 * Math.cos(12 * t); }); }, 0],
    ['ghostish', 'Ghostish', function () {
      var top = arc(0, 0, 1, 180, 360, 48), bot = [], i, x;
      for (i = 0; i <= 36; i++) { x = 1 - 2 * i / 36; bot.push([x, .95 + .12 * Math.sin(i / 36 * Math.PI * 6)]); }
      return top.concat(bot);
    }, 2],
    ['clover4', 'Clover4Leaf', function () { return polar(function (t) { return .5 + .5 * Math.pow(Math.abs(Math.cos(2 * t)), .75); }); }, 1],
    ['clover8', 'Clover8Leaf', function () { return polar(function (t) { return .62 + .38 * Math.pow(Math.abs(Math.cos(4 * t)), .8); }); }, 1],
    ['burst', 'Burst', function () { return star(12, .62); }, 1],
    ['softburst', 'SoftBurst', function () { return star(10, .72); }, 8],
    ['boom', 'Boom', function () { return star(9, .5); }, 0],
    ['softboom', 'SoftBoom', function () { return star(9, .64); }, 8],
    ['flower', 'Flower', function () { return polar(function (t) { return .6 + .4 * Math.abs(Math.cos(2.5 * t)); }); }, 2],
    ['puffy', 'Puffy', function () { return polar(function (t) { return .72 + .28 * Math.sqrt(Math.abs(Math.cos(3.5 * t))); }); }, 1],
    ['puffydiamond', 'PuffyDiamond', function () { return polar(function (t) { return .7 + .3 * Math.sqrt(Math.abs(Math.sin(2 * t))); }); }, 1],
    ['pixelcircle', 'PixelCircle', function () { return [[-.35, -1], [.35, -1], [.35, -.7], [.7, -.7], [.7, -.35], [1, -.35], [1, .35], [.7, .35], [.7, .7], [.35, .7], [.35, 1], [-.35, 1], [-.35, .7], [-.7, .7], [-.7, .35], [-1, .35], [-1, -.35], [-.7, -.35], [-.7, -.7], [-.35, -.7]]; }, 0],
    ['pixeltriangle', 'PixelTriangle', function () { return [[-.18, -1], [.18, -1], [.18, -.7], [.42, -.7], [.42, -.4], [.66, -.4], [.66, -.1], [.9, -.1], [.9, .2], [1.1, .2], [1.1, .9], [-1.1, .9], [-1.1, .2], [-.9, .2], [-.9, -.1], [-.66, -.1], [-.66, -.4], [-.42, -.4], [-.42, -.7], [-.18, -.7]]; }, 0],
    ['bun', 'Bun', function () { return superellipse(1, .85, 3).map(function (p) { return [p[0], p[1] > 0 ? p[1] * .8 : p[1]]; }); }, 0],
    ['heart', 'Heart', function () {
      var out = [], i, t;
      for (i = 0; i < 240; i++) { t = TAU * i / 240; out.push([16 * Math.pow(Math.sin(t), 3), -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t))]); }
      return out;
    }, 0]
  ];

  function centroid(poly) {
    var a = 0, cx = 0, cy = 0, n = poly.length, i, p, q, f;
    for (i = 0; i < n; i++) {
      p = poly[i]; q = poly[(i + 1) % n]; f = p[0] * q[1] - q[0] * p[1];
      a += f; cx += (p[0] + q[0]) * f; cy += (p[1] + q[1]) * f;
    }
    if (Math.abs(a) < 1e-9) return [0, 0];
    return [cx / (3 * a), cy / (3 * a)];
  }

  // Farthest intersection of a ray from (cx, cy) with the polygon boundary
  function castRadius(poly, cx, cy, ang) {
    var dx = Math.cos(ang), dy = Math.sin(ang), best = 0, n = poly.length, i, a, b, ex, ey, den, t, u;
    for (i = 0; i < n; i++) {
      a = poly[i]; b = poly[(i + 1) % n];
      ex = b[0] - a[0]; ey = b[1] - a[1];
      den = dx * ey - dy * ex;
      if (Math.abs(den) < 1e-12) continue;
      t = ((a[0] - cx) * ey - (a[1] - cy) * ex) / den;
      u = ((a[0] - cx) * dy - (a[1] - cy) * dx) / den;
      if (t > 1e-9 && u >= -1e-9 && u <= 1 + 1e-9 && t > best) best = t;
    }
    return best;
  }

  function blurCircular(arr, k) {
    if (!k) return arr;
    var n = arr.length, out = new Float32Array(n), i, j, s, w, tw;
    for (i = 0; i < n; i++) {
      s = 0; tw = 0;
      for (j = -k; j <= k; j++) { w = k + 1 - Math.abs(j); s += arr[(i + j + n) % n] * w; tw += w; }
      out[i] = s / tw;
    }
    return out;
  }

  function buildShape(def) {
    var poly = def[2](), soft = def[3], c = centroid(poly), r = new Float32Array(N), i, ang;
    for (i = 0; i < N; i++) r[i] = castRadius(poly, c[0], c[1], START + TAU * i / N);
    r = blurCircular(r, soft);
    // bounding box around the centroid so every shape sits centred and equally big
    var minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9, x, y;
    for (i = 0; i < N; i++) {
      ang = START + TAU * i / N; x = r[i] * Math.cos(ang); y = r[i] * Math.sin(ang);
      if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
    }
    var scale = 2 / Math.max(maxX - minX, maxY - minY);
    var rr = new Float32Array(N);
    for (i = 0; i < N; i++) rr[i] = r[i] * scale;
    return { id: def[0], name: def[1], r: rr, ox: (minX + maxX) / 2 * scale, oy: (minY + maxY) / 2 * scale };
  }

  var SHAPES = DEFS.map(buildShape);
  var BY_ID = {};
  SHAPES.forEach(function (s) { BY_ID[s.id] = s; });

  var cosT = new Float32Array(N), sinT = new Float32Array(N);
  (function () { for (var i = 0; i < N; i++) { var a = START + TAU * i / N; cosT[i] = Math.cos(a); sinT[i] = Math.sin(a); } })();

  var R = 44; // radius in the 100x100 viewBox (leaves room for overshoot)
  function f1(v) { return Math.round(v * 100) / 100; }

  // Closed quadratic B-spline through the sampled points: smooth and can never overshoot,
  // so sharp corners round off cleanly instead of growing little hooks.
  function pathFor(r, ox, oy) {
    var px = new Float32Array(N), py = new Float32Array(N), i, rad;
    for (i = 0; i < N; i++) {
      rad = r[i] < .02 ? .02 : r[i];
      px[i] = 50 + R * (rad * cosT[i] - ox); py[i] = 50 + R * (rad * sinT[i] - oy);
    }
    var d = 'M' + f1((px[N - 1] + px[0]) / 2) + ' ' + f1((py[N - 1] + py[0]) / 2), j;
    for (i = 0; i < N; i++) {
      j = (i + 1) % N;
      d += 'Q' + f1(px[i]) + ' ' + f1(py[i]) + ' ' + f1((px[i] + px[j]) / 2) + ' ' + f1((py[i] + py[j]) / 2);
    }
    return d + 'Z';
  }

  // Spring-driven morph between shapes
  function Morph(pathEl, startId) {
    var s = BY_ID[startId] || SHAPES[0];
    this.el = pathEl;
    this.r = new Float32Array(s.r); this.ox = s.ox; this.oy = s.oy;
    this.from = null; this.to = s;
    this.x = 1; this.v = 0; this.raf = 0; this.last = 0;
    this.el.setAttribute('d', pathFor(this.r, this.ox, this.oy));
  }
  Morph.prototype.set = function (id) {
    var s = BY_ID[id]; if (!s) return;
    this.to = s;
    if (reduceMotion) {
      this.r.set(s.r); this.ox = s.ox; this.oy = s.oy;
      this.el.setAttribute('d', pathFor(this.r, this.ox, this.oy));
      return;
    }
    this.from = { r: new Float32Array(this.r), ox: this.ox, oy: this.oy };
    this.x = 0; this.v = 0; this.last = 0;
    if (!this.raf) this.raf = requestAnimationFrame(this.tick.bind(this));
  };
  Morph.prototype.tick = function (now) {
    var self = this, dt = this.last ? Math.min((now - this.last) / 1000, 1 / 30) : 1 / 60;
    this.last = now;
    var k = 260, zeta = .52, c = 2 * zeta * Math.sqrt(k);   // bouncy spatial spring
    var steps = 2, h = dt / steps, i;
    for (i = 0; i < steps; i++) { this.v += (k * (1 - this.x) - c * this.v) * h; this.x += this.v * h; }
    var x = this.x, a = this.from, b = this.to;
    for (i = 0; i < N; i++) this.r[i] = a.r[i] + (b.r[i] - a.r[i]) * x;
    this.ox = a.ox + (b.ox - a.ox) * x; this.oy = a.oy + (b.oy - a.oy) * x;
    this.el.setAttribute('d', pathFor(this.r, this.ox, this.oy));
    if (Math.abs(1 - this.x) < .0008 && Math.abs(this.v) < .01) {
      this.r.set(b.r); this.ox = b.ox; this.oy = b.oy;
      this.el.setAttribute('d', pathFor(this.r, this.ox, this.oy));
      this.raf = 0; return;
    }
    this.raf = requestAnimationFrame(function (t) { self.tick(t); });
  };

  function staticPath(id) { var s = BY_ID[id]; return pathFor(s.r, s.ox, s.oy); }

  /* ======================================================================
     2. SHAPES IN THE PAGE
     ====================================================================== */
  // Decorative badges that use data-shape
  $$('[data-shape]').forEach(function (box) {
    var p = $('path', box), id = box.getAttribute('data-shape');
    if (p && BY_ID[id]) p.setAttribute('d', staticPath(id));
  });

  // Hero blob: cycles through shapes with a spring
  var heroBlob = $('#hero-blob');
  if (heroBlob) {
    var heroMorph = new Morph(heroBlob, 'cookie9');
    var seq = ['cookie9', 'softburst', 'flower', 'cookie6', 'sunny', 'clover4', 'puffy', 'cookie12'], si = 0, heroTimer = 0, heroVisible = true;
    var runHero = function () { clearInterval(heroTimer); if (reduceMotion || !heroVisible || document.hidden) return; heroTimer = setInterval(function () { si = (si + 1) % seq.length; heroMorph.set(seq[si]); }, 2600); };
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(function (e) { heroVisible = e[0].isIntersecting; runHero(); }).observe(heroBlob.ownerSVGElement.parentNode);
    }
    document.addEventListener('visibilitychange', runHero);
    runHero();
  }

  // Shape studio
  (function () {
    var grid = $('#shapes-catalog-grid'), heroP = $('#shape-hero-path');
    if (!grid || !heroP) return;
    var label = $('#shape-hero-label'), count = $('#shape-hero-count'), morph = new Morph(heroP, 'circle'), cur = 0, chips = [];

    function select(i, fromUser) {
      cur = (i + SHAPES.length) % SHAPES.length;
      chips.forEach(function (c, j) { c.setAttribute('aria-pressed', j === cur ? 'true' : 'false'); });
      morph.set(SHAPES[cur].id);
      label.textContent = SHAPES[cur].name;
      count.textContent = (cur + 1) + ' of ' + SHAPES.length;
      if (fromUser && navigator.vibrate) { try { navigator.vibrate(8); } catch (e) {} }
    }

    SHAPES.forEach(function (s, i) {
      var b = document.createElement('button');
      b.type = 'button'; b.className = 'shape-chip';
      b.setAttribute('aria-label', s.name); b.setAttribute('title', s.name);
      b.setAttribute('aria-pressed', i === 0 ? 'true' : 'false');
      b.innerHTML = '<svg viewBox="0 0 100 100" aria-hidden="true"><path d="' + staticPath(s.id) + '"/></svg>';
      b.addEventListener('click', function () { select(i, true); });
      grid.appendChild(b); chips.push(b);
    });

    $('#shape-hero').addEventListener('click', function () { select(cur + 1, true); });
    $('#shape-shuffle').addEventListener('click', function () {
      var n; do { n = Math.floor(Math.random() * SHAPES.length); } while (n === cur);
      select(n, true);
    });
  })();

  /* ======================================================================
     3. THEME ENGINE
     ====================================================================== */
  var themeBtn = $('#theme-toggle-btn'), darkSw = $('#dark-switch'), amoledSw = $('#amoled-switch'), swatches = $$('.swatch');

  function state() {
    return {
      mode: root.getAttribute('data-theme-mode') || 'light',
      palette: root.getAttribute('data-palette') || 'petal',
      amoled: root.getAttribute('data-amoled') === 'true'
    };
  }
  function syncUI() {
    var s = state(), dark = s.mode === 'dark';
    darkSw.setAttribute('aria-checked', dark ? 'true' : 'false');
    amoledSw.setAttribute('aria-checked', dark && s.amoled ? 'true' : 'false');
    swatches.forEach(function (b) { b.setAttribute('aria-checked', b.getAttribute('data-palette') === s.palette ? 'true' : 'false'); });
    // SVG elements ignore the .hidden property, so toggle the attribute
    if (dark) { $('.i-moon', themeBtn).setAttribute('hidden', ''); $('.i-sun', themeBtn).removeAttribute('hidden'); }
    else { $('.i-sun', themeBtn).setAttribute('hidden', ''); $('.i-moon', themeBtn).removeAttribute('hidden'); }
    themeBtn.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
  }
  function save() { try { localStorage.setItem('petal-theme', JSON.stringify(state())); } catch (e) {} }

  // Colour change that sweeps out from the control that was tapped
  function themed(change, origin) {
    if (!document.startViewTransition || reduceMotion) { change(); syncUI(); save(); return; }
    var r = origin ? origin.getBoundingClientRect() : { left: innerWidth / 2, top: innerHeight / 2, width: 0, height: 0 };
    var x = r.left + r.width / 2, y = r.top + r.height / 2;
    var end = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
    var t = document.startViewTransition(function () { change(); syncUI(); });
    t.ready.then(function () {
      root.animate({ clipPath: ['circle(0px at ' + x + 'px ' + y + 'px)', 'circle(' + end + 'px at ' + x + 'px ' + y + 'px)'] },
        { duration: 650, easing: 'cubic-bezier(.2,0,0,1)', pseudoElement: '::view-transition-new(root)' });
    }).catch(function () {});
    t.finished.then(save, save);
  }

  function setMode(dark) {
    root.setAttribute('data-theme-mode', dark ? 'dark' : 'light');
    if (!dark) root.setAttribute('data-amoled', 'false');
  }
  themeBtn.addEventListener('click', function () { themed(function () { setMode(state().mode !== 'dark'); }, themeBtn); });
  darkSw.addEventListener('click', function () { themed(function () { setMode(state().mode !== 'dark'); }, darkSw); });
  amoledSw.addEventListener('click', function () {
    themed(function () {
      var on = !(state().mode === 'dark' && state().amoled);
      root.setAttribute('data-amoled', on ? 'true' : 'false');
      if (on) root.setAttribute('data-theme-mode', 'dark');
    }, amoledSw);
  });
  swatches.forEach(function (b, i) {
    b.addEventListener('click', function () { themed(function () { root.setAttribute('data-palette', b.getAttribute('data-palette')); }, b); });
    b.addEventListener('keydown', function (e) {
      var d = e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? -1 : 0;
      if (!d) return; e.preventDefault();
      var n = swatches[(i + d + swatches.length) % swatches.length]; n.focus(); n.click();
    });
  });
  syncUI();

  /* ======================================================================
     4. APP BAR, MENU, SCROLL SPY
     ====================================================================== */
  var appbar = $('#appbar');
  function onScroll() { appbar.classList.toggle('scrolled', scrollY > 8); }
  addEventListener('scroll', onScroll, { passive: true }); onScroll();

  var menu = $('#menu'), menuBtn = $('#menu-btn');
  function setMenu(open) {
    menu.classList.toggle('open', open);
    menu.setAttribute('aria-hidden', open ? 'false' : 'true');
    menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
    menuBtn.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
    document.body.style.overflow = open ? 'hidden' : '';
    if (open) setTimeout(function () { var a = $('#menu-list a', menu); if (a) a.focus({ preventScroll: true }); }, 60);
    else if (document.activeElement && menu.contains(document.activeElement)) menuBtn.focus({ preventScroll: true });
  }
  menuBtn.addEventListener('click', function () { setMenu(!menu.classList.contains('open')); });
  $('#menu-close').addEventListener('click', function () { setMenu(false); });
  $('#menu-scrim').addEventListener('click', function () { setMenu(false); });
  $$('a', menu).forEach(function (a) { a.addEventListener('click', function () { setMenu(false); }); });
  addEventListener('keydown', function (e) { if (e.key === 'Escape') { if (menu.classList.contains('open')) setMenu(false); closeSheet(); } });
  matchMedia('(min-width: 960px)').addEventListener('change', function (e) { if (e.matches) setMenu(false); });

  // Scroll spy
  (function () {
    var ids = ['differentiators', 'shapes', 'media-player', 'architecture', 'specs'];
    var links = $$('#nav-links a, #menu-list a');
    if (!('IntersectionObserver' in window)) return;
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (!en.isIntersecting) return;
        links.forEach(function (a) { a.classList.toggle('active', a.getAttribute('href') === '#' + en.target.id); });
      });
    }, { rootMargin: '-40% 0px -55% 0px' });
    ids.forEach(function (id) { var el = document.getElementById(id); if (el) io.observe(el); });
    var hero = $('#hero');
    if (hero) new IntersectionObserver(function (e) { if (e[0].isIntersecting) links.forEach(function (a) { a.classList.remove('active'); }); }, { rootMargin: '-40% 0px -55% 0px' }).observe(hero);
  })();

  /* ======================================================================
     5. SNACKBAR + RIPPLE + REVEAL
     ====================================================================== */
  var snack = $('#snackbar'), snackTimer = 0;
  function toast(msg) {
    snack.textContent = msg; snack.classList.add('show');
    clearTimeout(snackTimer); snackTimer = setTimeout(function () { snack.classList.remove('show'); }, 2600);
  }

  document.addEventListener('pointerdown', function (e) {
    if (reduceMotion) return;
    var el = e.target.closest && e.target.closest('[data-ripple]');
    if (!el) return;
    var r = el.getBoundingClientRect(), size = Math.max(r.width, r.height) * 2.2, s = document.createElement('span');
    s.className = 'ripple';
    s.style.cssText = 'width:' + size + 'px;height:' + size + 'px;left:' + (e.clientX - r.left - size / 2) + 'px;top:' + (e.clientY - r.top - size / 2) + 'px';
    el.appendChild(s); setTimeout(function () { s.remove(); }, 650);
  }, { passive: true });

  if ('IntersectionObserver' in window) {
    var rv = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) { if (en.isIntersecting) { en.target.classList.add('in'); rv.unobserve(en.target); } });
    }, { rootMargin: '0px 0px -8% 0px', threshold: .08 });
    $$('[data-reveal]').forEach(function (el) { rv.observe(el); });
  } else {
    $$('[data-reveal]').forEach(function (el) { el.classList.add('in'); });
  }

  // Start the hero entrance once fonts are in (or after a short wait)
  var go = function () { requestAnimationFrame(function () { root.classList.add('ready'); }); };
  if (document.fonts && document.fonts.ready) { Promise.race([document.fonts.ready, new Promise(function (r) { setTimeout(r, 700); })]).then(go); } else go();

  /* ======================================================================
     6. PHONE DEMO
     ====================================================================== */
  var webview = $('#sim-webview'), omnibox = $('#sim-omnibox'), fab = $('#sim-fab');
  var sheet = $('#sim-sheet'), scrim = $('#sim-scrim');
  function closeSheet() { if (sheet) { sheet.classList.remove('show'); scrim.classList.remove('show'); } }
  if (webview) {
    var last = 0;
    webview.addEventListener('scroll', function () {
      var y = webview.scrollTop;
      if (y > last && y > 40) { omnibox.classList.add('collapsed'); fab.classList.add('show'); }
      else if (y < last - 2) { omnibox.classList.remove('collapsed'); fab.classList.remove('show'); }
      last = Math.max(0, y);
    }, { passive: true });
    fab.addEventListener('click', function () {
      omnibox.classList.remove('collapsed'); fab.classList.remove('show');
      webview.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' });
    });
    $('#sim-url-pill').addEventListener('click', function () { sheet.classList.add('show'); scrim.classList.add('show'); });
    scrim.addEventListener('click', closeSheet);

    var dock = $('#sim-dock'), dockBtns = $$('.dock-btn', dock);
    dockBtns.forEach(function (b, i) {
      b.addEventListener('click', function () {
        dock.style.setProperty('--idx', i);
        dockBtns.forEach(function (o) { o.setAttribute('aria-current', o === b ? 'true' : 'false'); });
      });
    });
  }

  /* ======================================================================
     7. WAVY PLAYER
     ====================================================================== */
  (function () {
    var canvas = $('#wavy-seekbar-canvas'), wrap = $('#wavy'), player = $('#player');
    if (!canvas) return;
    var ctx = canvas.getContext('2d'), playBtn = $('#player-play-btn'), timeEl = $('#player-time-current'), tag = $('#player-status-tag');
    var TOTAL = 245, playing = true, scrubbing = false, progress = .38, phase = 0, amp = 6, W = 0, H = 0, visible = true, raf = 0, lastT = 0, colors = {};

    // Cookie shape that spins while playing
    var art = $('.player-art path', player);
    if (art) art.setAttribute('d', staticPath('cookie9'));

    function readColors() {
      var probe = document.createElement('span'); probe.style.display = 'none'; document.body.appendChild(probe);
      var get = function (v) { probe.style.color = 'var(' + v + ')'; return getComputedStyle(probe).color; };
      colors = { on: get('--primary'), off: get('--outline-variant'), thumb: get('--primary') };
      probe.remove();
    }
    new MutationObserver(function () { setTimeout(readColors, 30); }).observe(root, { attributes: true, attributeFilter: ['data-theme-mode', 'data-palette', 'data-amoled'] });
    readColors();

    function resize() {
      var r = wrap.getBoundingClientRect(), dpr = Math.min(window.devicePixelRatio || 1, 3);
      W = r.width; H = r.height;
      canvas.width = Math.max(1, Math.round(W * dpr)); canvas.height = Math.max(1, Math.round(H * dpr));
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    if ('ResizeObserver' in window) new ResizeObserver(resize).observe(wrap); else addEventListener('resize', resize);
    resize();

    function fmt(s) { var m = Math.floor(s / 60), x = Math.floor(s % 60); return m + ':' + (x < 10 ? '0' : '') + x; }
    function setTag() { tag.textContent = scrubbing ? 'Scrubbing, flat' : playing ? 'Wavy sine' : 'Paused, flat'; }
    function updateTime() {
      var t = progress * TOTAL; timeEl.textContent = fmt(t);
      wrap.setAttribute('aria-valuenow', Math.round(t)); wrap.setAttribute('aria-valuetext', fmt(t) + ' of ' + fmt(TOTAL));
    }

    function draw(now) {
      var dt = lastT ? Math.min((now - lastT) / 1000, .05) : .016; lastT = now;
      var pad = 12, cy = H / 2, x0 = pad, x1 = W - pad, px = x0 + (x1 - x0) * progress;
      var target = (playing && !scrubbing) ? 6 : 0;
      amp += (target - amp) * Math.min(1, dt * 9);                  // wave eases flat / back
      if (playing && !scrubbing) { phase += dt * 5.2; progress += dt / 82; if (progress >= 1) progress = 0; updateTime(); }

      ctx.clearRect(0, 0, W, H); ctx.lineCap = 'round'; ctx.lineJoin = 'round';

      ctx.beginPath(); ctx.strokeStyle = colors.off; ctx.lineWidth = 5;
      ctx.moveTo(px + 8, cy); ctx.lineTo(x1, cy); ctx.stroke();

      ctx.beginPath(); ctx.strokeStyle = colors.on; ctx.lineWidth = 5;
      var f = .09, x;
      ctx.moveTo(x0, cy + Math.sin(x0 * f + phase) * amp);
      for (x = x0 + 2; x <= px; x += 2) ctx.lineTo(x, cy + Math.sin(x * f + phase) * amp);
      ctx.stroke();

      // handle: tall rounded bar, widens a little while scrubbing
      var hy = cy + Math.sin(px * f + phase) * amp, hw = scrubbing ? 3 : 5, hh = 28;
      ctx.beginPath(); ctx.fillStyle = colors.thumb;
      if (ctx.roundRect) ctx.roundRect(px - hw / 2, hy - hh / 2, hw, hh, hw / 2); else ctx.rect(px - hw / 2, hy - hh / 2, hw, hh);
      ctx.fill();

      raf = requestAnimationFrame(draw);
    }
    function start() { if (!raf && visible && !document.hidden) { lastT = 0; raf = requestAnimationFrame(draw); } }
    function stop() { cancelAnimationFrame(raf); raf = 0; }
    if ('IntersectionObserver' in window) new IntersectionObserver(function (e) { visible = e[0].isIntersecting; visible ? start() : stop(); }).observe(wrap);
    document.addEventListener('visibilitychange', function () { document.hidden ? stop() : start(); });
    start();

    function setPlaying(p) {
      playing = p;
      playBtn.setAttribute('aria-pressed', p ? 'true' : 'false'); playBtn.setAttribute('aria-label', p ? 'Pause' : 'Play');
      player.classList.toggle('playing', p); setTag();
    }
    playBtn.addEventListener('click', function () { setPlaying(!playing); });
    $('#player-back').addEventListener('click', function () { progress = Math.max(0, progress - 10 / TOTAL); updateTime(); });
    $('#player-fwd').addEventListener('click', function () { progress = Math.min(1, progress + 10 / TOTAL); updateTime(); });

    function scrubTo(e) {
      var r = wrap.getBoundingClientRect(), x = Math.max(12, Math.min(r.width - 12, e.clientX - r.left));
      progress = (x - 12) / (r.width - 24); updateTime();
    }
    wrap.addEventListener('pointerdown', function (e) { scrubbing = true; wrap.setPointerCapture(e.pointerId); setTag(); scrubTo(e); });
    wrap.addEventListener('pointermove', function (e) { if (scrubbing) scrubTo(e); });
    var end = function () { if (scrubbing) { scrubbing = false; setTag(); } };
    wrap.addEventListener('pointerup', end); wrap.addEventListener('pointercancel', end);
    wrap.addEventListener('keydown', function (e) {
      var d = e.key === 'ArrowRight' ? 5 : e.key === 'ArrowLeft' ? -5 : 0; if (!d) return;
      e.preventDefault(); progress = Math.max(0, Math.min(1, progress + d / TOTAL)); updateTime();
    });
    updateTime(); setTag();
  })();

  /* ======================================================================
     8. ARCHITECTURE TABS
     ====================================================================== */
  (function () {
    var tabs = $$('.arch-tab'), panel = $('#arch-panel');
    var badge = $('#arch-detail-badge'), title = $('#arch-detail-title'), desc = $('#arch-detail-desc'), code = $('#arch-detail-code');
    if (!tabs.length) return;
    var DATA = {
      geckoview: { badge: 'Independent Quantum Core', title: 'GeckoView 158 Standalone Engine', desc: 'Petal embeds Mozilla GeckoView 158 with an isolated quantum multi-process pipeline. It does not use the OEM Android WebView, preventing vendor delays and third-party tracking hooks.', code: 'GeckoRuntime.create(context, GeckoRuntimeSettings.Builder()\n  .aboutConfigEnabled(true)\n  .contentBlocking(ContentBlocking.STRICT)\n  .build())' },
      compose: { badge: 'Material 3 Expressive UI', title: 'Jetpack Compose & Spring Motion', desc: 'Constructed strictly using Jetpack Compose with Material 3 Expressive 1.5-alpha components, low bouncy spring physics (0.75f damping), and 35 mathematical shape geometries.', code: 'PetalExpressiveTheme(darkTheme = isDark, dynamicColor = true) {\n  Surface(shape = PetalMaterialShapes.Flower.toShape()) {\n    AddressBarCollapsible()\n  }\n}' },
      etp: { badge: 'Zero-Telemetry Isolation', title: 'Strict Content & Anti-Tracking Shield', desc: 'Enhanced Tracking Protection rejects third-party cookies, tracking beacons, cryptominers, and canvas fingerprinting. Session databases are encrypted locally using Room and SQLCipher.', code: 'settings.contentBlocking = ContentBlocking.STRICT\nsettings.cookieBehavior = GeckoSessionSettings.COOKIE_BEHAVIOR_REJECT_TRACKERS' },
      media: { badge: 'Compose Media Subsystem', title: 'mpvEx Overlay & Wavy Seekbar', desc: 'Custom Compose video player overlay featuring gesture-driven brightness/volume sliders, smart PiP aspect preservation, and mathematical sine wave animation during playback.', code: 'WavySeekbar(progress = playbackTime / duration,\n  waveState = if (isPlaying && !isScrubbing) Wave.ACTIVE else Wave.FLAT)' }
    };
    var order = ['geckoview', 'compose', 'etp', 'media'], idx = 0, first = true;

    function show(i, focus) {
      idx = (i + tabs.length) % tabs.length;
      tabs.forEach(function (t, j) { t.setAttribute('aria-selected', j === idx ? 'true' : 'false'); t.tabIndex = j === idx ? 0 : -1; });
      panel.setAttribute('aria-labelledby', tabs[idx].id);
      if (focus) tabs[idx].focus();
      var d = DATA[order[idx]];
      var swap = function () { badge.textContent = d.badge; title.textContent = d.title; desc.textContent = d.desc; code.textContent = d.code; };
      if (reduceMotion || first || !panel.animate) { swap(); first = false; return; }
      panel.animate([{ opacity: 1, transform: 'none' }, { opacity: 0, transform: 'translateY(10px) scale(.985)' }], { duration: 120, easing: 'ease-in' })
        .finished.then(function () {
          swap();
          panel.animate([{ opacity: 0, transform: 'translateY(-14px) scale(.985)' }, { opacity: 1, transform: 'none' }], { duration: 480, easing: 'cubic-bezier(.34,1.4,.64,1)' });
        }).catch(swap);
    }
    tabs.forEach(function (t, i) {
      t.addEventListener('click', function () { show(i); });
      t.addEventListener('keydown', function (e) {
        var d = e.key === 'ArrowDown' || e.key === 'ArrowRight' ? 1 : e.key === 'ArrowUp' || e.key === 'ArrowLeft' ? -1 : 0;
        if (e.key === 'Home') { e.preventDefault(); show(0, true); } else if (e.key === 'End') { e.preventDefault(); show(tabs.length - 1, true); }
        else if (d) { e.preventDefault(); show(idx + d, true); }
      });
    });
    first = false;
  })();

  /* ======================================================================
     9. COPY ADB COMMAND
     ====================================================================== */
  (function () {
    var btn = $('#cli-copy-btn'); if (!btn) return;
    var text = $('#cli-code').textContent.trim(), label = $('span', btn), timer = 0;
    function done() {
      btn.classList.add('done'); label.textContent = 'Copied'; toast('Command copied');
      clearTimeout(timer); timer = setTimeout(function () { btn.classList.remove('done'); label.textContent = 'Copy command'; }, 1800);
    }
    function fallback() {
      var t = document.createElement('textarea'); t.value = text; t.style.cssText = 'position:fixed;opacity:0'; document.body.appendChild(t); t.select();
      try { document.execCommand('copy'); done(); } catch (e) { toast('Press and hold the command to copy'); }
      t.remove();
    }
    btn.addEventListener('click', function () {
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(done, fallback); else fallback();
    });
  })();

  /* ======================================================================
     10. LIQUID GLASS DEMO
     Background layer (Glass / Morphs / Image), opacity, sheen and the live backdrop.
     ====================================================================== */
  (function () {
    var stage = $('#lg-stage'); if (!stage) return;
    var op = $('#lg-opacity'), sh = $('#lg-sheen'), opOut = $('#lg-opacity-out'), shOut = $('#lg-sheen-out');
    var live = $('#lg-live-switch'), seg = $$('.lg-seg button');

    function apply() {
      stage.style.setProperty('--lg-op', (op.value / 100).toFixed(2));
      stage.style.setProperty('--lg-sheen', (sh.value / 100).toFixed(2));
      opOut.textContent = op.value + '%'; shOut.textContent = sh.value + '%';
    }
    op.addEventListener('input', apply);
    sh.addEventListener('input', apply);
    apply();

    seg.forEach(function (b) {
      b.addEventListener('click', function () {
        seg.forEach(function (x) { x.setAttribute('aria-checked', String(x === b)); });
        stage.setAttribute('data-bg', b.getAttribute('data-bg'));
      });
    });

    live.addEventListener('click', function () {
      var on = live.getAttribute('aria-checked') !== 'true';
      live.setAttribute('aria-checked', String(on));
      stage.setAttribute('data-live', String(on));
    });
  })();
})();
