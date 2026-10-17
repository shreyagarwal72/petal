/* Petal changelog: live search over releases, and opens the FOSS Browser history when a link points into it. */
(function () {
  var q = document.getElementById('cl-search');
  if (!q) return;
  var items = [].slice.call(document.querySelectorAll('.release'));
  var texts = items.map(function (el) { return el.textContent.toLowerCase(); });
  var tocLinks = [].slice.call(document.querySelectorAll('.toc--versions li'));
  var empty = document.getElementById('cl-empty');
  var count = document.getElementById('cl-count');
  var details = document.getElementById('fb-details');

  function run() {
    var t = q.value.trim().toLowerCase(), n = 0;
    items.forEach(function (el, i) {
      var hit = !t || texts[i].indexOf(t) > -1;
      el.hidden = !hit;
      if (hit) n++;
    });
    tocLinks.forEach(function (li) {
      var a = li.querySelector('a'), id = a && a.getAttribute('href').slice(1), el = id && document.getElementById(id);
      if (el && el.classList.contains('release')) li.hidden = el.hidden;
    });
    empty.hidden = n > 0;
    count.textContent = t ? n + ' of ' + items.length + ' releases' : '';
    if (t && details && details.querySelector('.release:not([hidden])')) details.open = true;
  }

  function reveal() {
    var id = decodeURIComponent(location.hash.slice(1));
    var el = id && document.getElementById(id);
    if (!el) return;
    var d = el.closest('details');
    if (d) d.open = true;
    if (d || el.id === 'foss-browser-history') el.scrollIntoView();
  }

  q.addEventListener('input', run);
  addEventListener('hashchange', reveal);
  reveal();
})();
