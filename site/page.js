/* Petal — behaviour for sub-pages: theme toggle (shares the 'petal-theme' key with the home page) and the mobile menu. */
(function () {
  var $ = function (s, c) { return (c || document).querySelector(s); };
  var root = document.documentElement;

  var themeBtn = $('#theme-toggle-btn');
  function syncTheme() {
    var dark = root.getAttribute('data-theme-mode') === 'dark';
    var moon = $('.i-moon', themeBtn), sun = $('.i-sun', themeBtn);
    if (dark) { moon.setAttribute('hidden', ''); sun.removeAttribute('hidden'); }
    else { sun.setAttribute('hidden', ''); moon.removeAttribute('hidden'); }
    themeBtn.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
  }
  if (themeBtn) {
    syncTheme();
    themeBtn.addEventListener('click', function () {
      var dark = root.getAttribute('data-theme-mode') !== 'dark';
      var palette = root.getAttribute('data-palette') || 'petal';
      var amoled = root.getAttribute('data-amoled') === 'true';
      var apply = function () {
        root.setAttribute('data-theme-mode', dark ? 'dark' : 'light');
        if (!dark) root.setAttribute('data-amoled', 'false');
        syncTheme();
      };
      if (document.startViewTransition && !matchMedia('(prefers-reduced-motion: reduce)').matches) document.startViewTransition(apply); else apply();
      try { localStorage.setItem('petal-theme', JSON.stringify({ mode: dark ? 'dark' : 'light', palette: palette, amoled: dark && amoled })); } catch (e) {}
    });
  }

  var menu = $('#menu'), menuBtn = $('#menu-btn');
  if (!menu || !menuBtn) return;
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
  Array.prototype.forEach.call(menu.querySelectorAll('a'), function (a) { a.addEventListener('click', function () { setMenu(false); }); });
  addEventListener('keydown', function (e) { if (e.key === 'Escape' && menu.classList.contains('open')) setMenu(false); });
  matchMedia('(min-width: 960px)').addEventListener('change', function (e) { if (e.matches) setMenu(false); });
})();
