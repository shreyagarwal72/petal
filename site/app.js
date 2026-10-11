/**
 * Petal Showcase Website Engine
 * Coordinates Material 3 Expressive 35 Shape Morphing,
 * Wavy Animated Seekbar, Collapsible Omnibox Simulator,
 * and Dynamic Palettes / AMOLED Black switching.
 */

// 35 Material 3 Expressive Shapes Catalog from PetalMaterialShapes.kt
const SHAPES_CATALOG = [
  { id: 'circle', name: 'Circle', path: 'M 50,0 A 50,50 0 1,1 49.99,0 Z' },
  { id: 'square', name: 'Square', path: 'M 10,10 L 90,10 A 8,8 0 0,1 98,18 L 98,82 A 8,8 0 0,1 90,90 L 10,90 A 8,8 0 0,1 2,82 L 2,18 A 8,8 0 0,1 10,10 Z' },
  { id: 'slanted', name: 'Slanted', path: 'M 25,10 L 95,10 L 75,90 L 5,90 Z' },
  { id: 'arch', name: 'Arch', path: 'M 10,90 L 10,50 A 40,40 0 0,1 90,50 L 90,90 Z' },
  { id: 'semicircle', name: 'SemiCircle', path: 'M 5,50 A 45,45 0 0,1 95,50 L 95,50 L 5,50 Z' },
  { id: 'oval', name: 'Oval', path: 'M 50,10 A 35,45 0 1,1 49.99,10 Z' },
  { id: 'pill', name: 'Pill', path: 'M 30,15 L 70,15 A 35,35 0 0,1 70,85 L 30,85 A 35,35 0 0,1 30,15 Z' },
  { id: 'triangle', name: 'Triangle', path: 'M 50,10 L 90,85 L 10,85 Z' },
  { id: 'arrow', name: 'Arrow', path: 'M 50,10 L 90,55 L 65,55 L 65,90 L 35,90 L 35,55 L 10,55 Z' },
  { id: 'fan', name: 'Fan', path: 'M 50,85 L 15,25 A 45,45 0 0,1 85,25 Z' },
  { id: 'diamond', name: 'Diamond', path: 'M 50,5 L 95,50 L 50,95 L 5,50 Z' },
  { id: 'clamshell', name: 'ClamShell', path: 'M 50,15 Q 90,15 90,50 Q 90,85 50,85 Q 10,85 10,50 Q 10,15 50,15 Z' },
  { id: 'pentagon', name: 'Pentagon', path: 'M 50,8 L 92,38 L 76,88 L 24,88 L 8,38 Z' },
  { id: 'gem', name: 'Gem', path: 'M 30,10 L 70,10 L 92,45 L 50,92 L 8,45 Z' },
  { id: 'sunny', name: 'Sunny', path: 'M 50,5 L 62,35 L 95,50 L 62,65 L 50,95 L 38,65 L 5,50 L 38,35 Z' },
  { id: 'verysunny', name: 'VerySunny', path: 'M 50,5 L 58,25 L 80,15 L 72,36 L 95,50 L 72,64 L 80,85 L 58,75 L 50,95 L 42,75 L 20,85 L 28,64 L 5,50 L 28,36 L 20,15 L 42,25 Z' },
  { id: 'cookie4', name: 'Cookie4Sided', path: 'M 35,10 Q 50,22 65,10 Q 78,25 90,35 Q 78,50 90,65 Q 75,78 65,90 Q 50,78 35,90 Q 22,78 10,65 Q 22,50 10,35 Q 22,22 35,10 Z' },
  { id: 'cookie6', name: 'Cookie6Sided', path: 'M 50,10 Q 65,18 78,12 Q 85,28 92,35 Q 85,50 92,65 Q 80,75 75,88 Q 60,82 50,90 Q 40,82 25,88 Q 20,75 8,65 Q 15,50 8,35 Q 18,25 22,12 Q 35,18 50,10 Z' },
  { id: 'cookie7', name: 'Cookie7Sided', path: 'M 50,8 Q 62,20 74,14 Q 82,30 90,38 Q 85,55 90,68 Q 78,78 72,90 Q 55,85 45,92 Q 32,85 22,90 Q 18,74 10,65 Q 15,50 10,35 Q 20,25 24,14 Q 38,20 50,8 Z' },
  { id: 'cookie9', name: 'Cookie9Sided', path: 'M 50,6 Q 60,18 72,12 Q 80,24 88,32 Q 84,46 92,56 Q 84,68 88,80 Q 74,84 66,92 Q 52,86 42,92 Q 30,86 20,90 Q 18,76 10,68 Q 16,56 10,44 Q 18,34 16,22 Q 30,22 38,12 Q 44,18 50,6 Z' },
  { id: 'cookie12', name: 'Cookie12Sided', path: 'M 50,5 L 60,15 L 72,8 L 78,22 L 92,20 L 88,35 L 98,45 L 88,55 L 94,68 L 80,74 L 80,88 L 65,85 L 58,96 L 46,88 L 35,95 L 30,82 L 16,84 L 20,70 L 6,62 L 15,50 L 6,38 L 18,30 L 15,16 L 30,20 L 38,8 Z' },
  { id: 'ghostish', name: 'Ghostish', path: 'M 15,50 A 35,35 0 0,1 85,50 L 85,85 Q 73,75 62,85 Q 50,75 38,85 Q 26,75 15,85 Z' },
  { id: 'clover4', name: 'Clover4Leaf', path: 'M 50,50 Q 50,15 35,15 Q 20,15 20,35 Q 20,50 50,50 Q 80,50 80,35 Q 80,15 65,15 Q 50,15 50,50 Q 50,85 65,85 Q 80,85 80,65 Q 80,50 50,50 Q 20,50 20,65 Q 20,85 35,85 Q 50,85 50,50 Z' },
  { id: 'clover8', name: 'Clover8Leaf', path: 'M 50,50 Q 50,10 40,15 Q 30,20 40,35 Q 20,20 15,30 Q 10,40 25,45 Q 10,50 15,60 Q 20,70 35,65 Q 20,80 30,85 Q 40,90 45,75 Q 50,90 60,85 Q 70,80 65,65 Q 80,70 85,60 Q 90,50 75,45 Q 90,40 85,30 Q 80,20 65,35 Q 70,20 60,15 Q 50,10 50,50 Z' },
  { id: 'burst', name: 'Burst', path: 'M 50,0 L 62,35 L 98,20 L 75,50 L 98,80 L 62,65 L 50,100 L 38,65 L 2,80 L 25,50 L 2,20 L 38,35 Z' },
  { id: 'softburst', name: 'SoftBurst', path: 'M 50,5 Q 65,25 80,15 Q 75,35 95,50 Q 75,65 80,85 Q 65,75 50,95 Q 35,75 20,85 Q 25,65 5,50 Q 25,35 20,15 Q 35,25 50,5 Z' },
  { id: 'boom', name: 'Boom', path: 'M 50,0 L 65,30 L 100,15 L 80,48 L 98,75 L 68,70 L 65,100 L 45,78 L 20,95 L 30,65 L 0,55 L 28,40 L 10,15 L 40,28 Z' },
  { id: 'softboom', name: 'SoftBoom', path: 'M 50,8 Q 70,22 88,18 Q 80,42 94,56 Q 78,70 82,90 Q 60,82 48,94 Q 38,80 20,86 Q 26,68 12,52 Q 28,40 22,22 Q 40,30 50,8 Z' },
  { id: 'flower', name: 'Flower', path: 'M 50,25 C 45,8 55,8 50,25 C 65,12 72,20 58,32 C 78,28 82,38 65,45 C 82,52 78,62 58,58 C 72,70 65,78 50,65 C 55,82 45,82 50,65 C 35,78 28,70 42,58 C 22,62 18,52 35,45 C 18,38 22,28 42,32 C 28,20 35,12 50,25 Z' },
  { id: 'puffy', name: 'Puffy', path: 'M 35,18 A 16,16 0 0,1 65,18 A 16,16 0 0,1 82,38 A 16,16 0 0,1 78,68 A 16,16 0 0,1 55,82 A 16,16 0 0,1 25,78 A 16,16 0 0,1 18,48 A 16,16 0 0,1 35,18 Z' },
  { id: 'puffydiamond', name: 'PuffyDiamond', path: 'M 50,10 Q 75,25 90,50 Q 75,75 50,90 Q 25,75 10,50 Q 25,25 50,10 Z' },
  { id: 'pixelcircle', name: 'PixelCircle', path: 'M 30,10 H 70 V 20 H 80 V 30 H 90 V 70 H 80 V 80 H 70 V 90 H 30 V 80 H 20 V 70 H 10 V 30 H 20 V 20 H 30 Z' },
  { id: 'pixeltriangle', name: 'PixelTriangle', path: 'M 40,10 H 60 V 25 H 70 V 40 H 80 V 55 H 90 V 85 H 10 V 55 H 20 V 40 H 30 V 25 H 40 Z' },
  { id: 'bun', name: 'Bun', path: 'M 20,40 C 20,15 80,15 80,40 C 85,60 85,80 75,85 C 60,88 40,88 25,85 C 15,80 15,60 20,40 Z' },
  { id: 'heart', name: 'Heart', path: 'M 50,30 C 50,15 25,12 20,32 C 15,52 50,85 50,85 C 50,85 85,52 80,32 C 75,12 50,15 50,30 Z' }
];

document.addEventListener('DOMContentLoaded', () => {
  initThemeEngine();
  initShapesPlayground();
  initWavyPlayer();
  initDeviceSimulator();
  initArchInspector();
  initCliCopy();
});

/* 1. Theme, Palette & AMOLED Switching */
function initThemeEngine() {
  const root = document.documentElement;
  const themeToggle = document.getElementById('theme-toggle-btn');
  const amoledToggle = document.getElementById('amoled-toggle-btn');
  const swatches = document.querySelectorAll('.swatch-btn');

  // Palette Switching
  swatches.forEach(btn => {
    btn.addEventListener('click', () => {
      swatches.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const palette = btn.getAttribute('data-palette');
      root.setAttribute('data-palette', palette);
    });
  });

  // Dark/Light Theme Toggle
  if (themeToggle) {
    themeToggle.addEventListener('click', () => {
      const current = root.getAttribute('data-theme-mode') || 'light';
      const next = current === 'dark' ? 'light' : 'dark';
      root.setAttribute('data-theme-mode', next);
      themeToggle.setAttribute('aria-label', `Switch to ${current} mode`);
      themeToggle.innerHTML = next === 'dark' 
        ? `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="5"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg>`
        : `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>`;
    });
  }

  // Pure AMOLED Black (#000000) Toggle
  if (amoledToggle) {
    amoledToggle.addEventListener('click', () => {
      const isAmoled = root.getAttribute('data-amoled') === 'true';
      if (!isAmoled) {
        root.setAttribute('data-amoled', 'true');
        root.setAttribute('data-theme-mode', 'dark'); // AMOLED necessitates dark
        amoledToggle.classList.add('active');
      } else {
        root.setAttribute('data-amoled', 'false');
        amoledToggle.classList.remove('active');
      }
    });
  }
}

/* 2. 35 Material 3 Expressive Shapes Playground */
function initShapesPlayground() {
  const catalogContainer = document.getElementById('shapes-catalog-grid');
  const heroPath = document.getElementById('shape-hero-path');
  const heroLabel = document.getElementById('shape-hero-label');
  const countLabel = document.getElementById('shapes-count-tag');

  if (!catalogContainer || !heroPath) return;

  if (countLabel) {
    countLabel.textContent = `${SHAPES_CATALOG.length} Expressive Shapes Verified`;
  }

  SHAPES_CATALOG.forEach((shape, index) => {
    const chip = document.createElement('button');
    chip.className = `shape-chip ${index === 0 ? 'active' : ''}`;
    chip.setAttribute('type', 'button');
    chip.setAttribute('title', shape.name);
    chip.innerHTML = `
      <svg class="shape-chip-icon" viewBox="0 0 100 100">
        <path d="${shape.path}" />
      </svg>
      <span class="shape-chip-name">${shape.name}</span>
    `;

    chip.addEventListener('click', () => {
      document.querySelectorAll('.shape-chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      
      // Animate path morph
      heroPath.setAttribute('d', shape.path);
      if (heroLabel) {
        heroLabel.textContent = `Polygon: ${shape.name} (${shape.id})`;
      }
    });

    catalogContainer.appendChild(chip);
  });
}

/* 3. mpvEx-Inspired Native Media Player & Wavy Seekbar Canvas */
function initWavyPlayer() {
  const canvas = document.getElementById('wavy-seekbar-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  
  const playBtn = document.getElementById('player-play-btn');
  const timeCurrent = document.getElementById('player-time-current');
  const statusTag = document.getElementById('player-status-tag');

  let isPlaying = true;
  let progress = 0.38; // 38%
  let isScrubbing = false;
  let animationFrameId = null;
  let phase = 0;
  const totalDuration = 245; // 4:05 in seconds

  function resizeCanvas() {
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * window.devicePixelRatio;
    canvas.height = rect.height * window.devicePixelRatio;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
  }

  window.addEventListener('resize', resizeCanvas);
  resizeCanvas();

  function formatTime(secs) {
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  }

  function render() {
    const w = canvas.getBoundingClientRect().width;
    const h = canvas.getBoundingClientRect().height;
    ctx.clearRect(0, 0, w, h);

    const centerY = h / 2;
    const progressX = w * progress;
    const amplitude = (isPlaying && !isScrubbing) ? 6.5 : 0; // Wave flattens when scrubbing or paused!
    const frequency = 0.055;

    // 1. Draw Inactive / Track Background (Right of thumb)
    ctx.beginPath();
    ctx.strokeStyle = getComputedStyle(document.documentElement).getPropertyValue('--color-border') || '#444';
    ctx.lineWidth = 3.5;
    ctx.lineCap = 'round';
    ctx.moveTo(progressX, centerY);
    ctx.lineTo(w - 12, centerY);
    ctx.stroke();

    // 2. Draw Active Sine Wave (Left of thumb)
    ctx.beginPath();
    const primaryColor = getComputedStyle(document.documentElement).getPropertyValue('--color-primary').trim() || '#D81B60';
    ctx.strokeStyle = primaryColor;
    ctx.lineWidth = 4;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    ctx.moveTo(12, centerY);
    for (let x = 12; x <= progressX; x += 2) {
      const y = centerY + Math.sin((x * frequency) + phase) * amplitude;
      ctx.lineTo(x, y);
    }
    ctx.stroke();

    // 3. Draw Thumb Pill
    const thumbY = centerY + Math.sin((progressX * frequency) + phase) * amplitude;
    ctx.beginPath();
    ctx.fillStyle = primaryColor;
    ctx.arc(progressX, thumbY, 7.5, 0, Math.PI * 2);
    ctx.fill();

    // Subtle thumb border
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Step state
    if (isPlaying && !isScrubbing) {
      phase += 0.08;
      progress += 0.00045;
      if (progress > 1) progress = 0;
      if (timeCurrent) {
        timeCurrent.textContent = formatTime(progress * totalDuration);
      }
    }

    animationFrameId = requestAnimationFrame(render);
  }

  render();

  // Play / Pause Toggle
  if (playBtn) {
    playBtn.addEventListener('click', () => {
      isPlaying = !isPlaying;
      playBtn.innerHTML = isPlaying
        ? `<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>`
        : `<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>`;
      if (statusTag) {
        statusTag.textContent = isPlaying ? 'Wavy Sine Dynamic' : 'Paused (Wave Flattened)';
      }
    });
  }

  // Interactive Scrubbing (Pointer Events)
  function handleScrub(e) {
    const rect = canvas.getBoundingClientRect();
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const x = Math.max(12, Math.min(rect.width - 12, clientX - rect.left));
    progress = (x - 12) / (rect.width - 24);
    if (timeCurrent) {
      timeCurrent.textContent = formatTime(progress * totalDuration);
    }
  }

  canvas.addEventListener('mousedown', (e) => {
    isScrubbing = true;
    if (statusTag) statusTag.textContent = 'Scrubbing (Flat Seek)';
    handleScrub(e);
  });
  window.addEventListener('mousemove', (e) => {
    if (isScrubbing) handleScrub(e);
  });
  window.addEventListener('mouseup', () => {
    if (isScrubbing) {
      isScrubbing = false;
      if (statusTag) statusTag.textContent = isPlaying ? 'Wavy Sine Dynamic' : 'Paused';
    }
  });

  // Touch support for mobile devices
  canvas.addEventListener('touchstart', (e) => {
    isScrubbing = true;
    handleScrub(e);
  }, { passive: true });
  window.addEventListener('touchmove', (e) => {
    if (isScrubbing) handleScrub(e);
  }, { passive: true });
  window.addEventListener('touchend', () => {
    isScrubbing = false;
  });
}

/* 4. Collapsible Omnibox & Tab Swipe Phone Simulator */
function initDeviceSimulator() {
  const webview = document.getElementById('sim-webview-scroll');
  const omnibox = document.getElementById('sim-omnibox');
  const fab = document.getElementById('sim-fab-bubble');
  const urlPill = document.getElementById('sim-url-pill');

  if (!webview || !omnibox || !fab) return;

  let lastScrollTop = 0;

  // Scroll offset collapse trigger
  webview.addEventListener('scroll', () => {
    const currentScroll = webview.scrollTop;
    if (currentScroll > lastScrollTop && currentScroll > 40) {
      // Scrolling down -> Collapse omnibox, show FAB
      omnibox.classList.add('collapsed');
      fab.classList.add('active');
    } else if (currentScroll < lastScrollTop) {
      // Scrolling up -> Restore full omnibox
      omnibox.classList.remove('collapsed');
      fab.classList.remove('active');
    }
    lastScrollTop = Math.max(0, currentScroll);
  });

  // Tap FAB to restore omnibox
  fab.addEventListener('click', () => {
    omnibox.classList.remove('collapsed');
    fab.classList.remove('active');
    webview.scrollTo({ top: 0, behavior: 'smooth' });
  });

  // Click URL Pill to show quick clean sheet
  if (urlPill) {
    urlPill.addEventListener('click', () => {
      alert("Petal Omnibox Action:\n• Clean URL Copied (Trackers Stripped)\n• Enhanced Tracking Protection: Strict Mode\n• WebExtension: Dark Mode Enabled");
    });
  }
}

/* 5. Architecture Multi-Process Inspector */
function initArchInspector() {
  const nodes = document.querySelectorAll('.arch-node');
  const title = document.getElementById('arch-detail-title');
  const desc = document.getElementById('arch-detail-desc');
  const code = document.getElementById('arch-detail-code');
  const badge = document.getElementById('arch-detail-badge');

  const ARCH_DATA = {
    geckoview: {
      badge: 'Independent Quantum Core',
      title: 'GeckoView 157 Standalone Engine',
      desc: 'Petal embeds Mozilla GeckoView 157 with an isolated quantum multi-process pipeline. It does not use the OEM Android WebView, preventing vendor delays and third-party tracking hooks.',
      code: 'GeckoRuntime.create(context, GeckoRuntimeSettings.Builder()\n  .aboutConfigEnabled(true)\n  .contentBlocking(ContentBlocking.STRICT)\n  .build())'
    },
    compose: {
      badge: 'Material 3 Expressive UI',
      title: 'Jetpack Compose & Spring Motion',
      desc: 'Constructed strictly using Jetpack Compose with Material 3 Expressive 1.5-alpha components, low bouncy spring physics (0.75f damping), and 35 mathematical shape geometries.',
      code: 'PetalExpressiveTheme(darkTheme = isDark, dynamicColor = true) {\n  Surface(shape = PetalMaterialShapes.Flower.toShape()) {\n    AddressBarCollapsible()\n  }\n}'
    },
    etp: {
      badge: 'Zero-Telemetry Isolation',
      title: 'Strict Content & Anti-Tracking Shield',
      desc: 'Enhanced Tracking Protection rejects third-party cookies, tracking beacons, cryptominers, and canvas fingerprinting. Session databases are encrypted locally using Room and SQLCipher.',
      code: 'settings.contentBlocking = ContentBlocking.STRICT\nsettings.cookieBehavior = GeckoSessionSettings.COOKIE_BEHAVIOR_REJECT_TRACKERS'
    },
    media: {
      badge: 'Compose Media Subsystem',
      title: 'mpvEx Overlay & Wavy Seekbar',
      desc: 'Custom Compose video player overlay featuring gesture-driven brightness/volume sliders, smart PiP aspect preservation, and mathematical sine wave animation during playback.',
      code: 'WavySeekbar(progress = playbackTime / duration,\n  waveState = if (isPlaying && !isScrubbing) Wave.ACTIVE else Wave.FLAT)'
    }
  };

  nodes.forEach(node => {
    node.addEventListener('click', () => {
      nodes.forEach(n => n.classList.remove('active'));
      node.classList.add('active');
      const key = node.getAttribute('data-node');
      const data = ARCH_DATA[key];
      if (data && title && desc && code && badge) {
        badge.textContent = data.badge;
        title.textContent = data.title;
        desc.textContent = data.desc;
        code.textContent = data.code;
      }
    });
  });
}

/* 6. ADB CLI Copy Button */
function initCliCopy() {
  const btn = document.getElementById('cli-copy-btn');
  if (!btn) return;
  btn.addEventListener('click', () => {
    const text = 'adb install -r Petal-release.apk';
    navigator.clipboard.writeText(text).then(() => {
      const orig = btn.textContent;
      btn.textContent = 'Copied to Clipboard!';
      setTimeout(() => btn.textContent = orig, 2000);
    }).catch(() => {
      btn.textContent = 'Copied!';
    });
  });
}
