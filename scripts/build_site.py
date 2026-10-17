#!/usr/bin/env python3
"""Build Petal's self-hosted documentation pages.

Reads the Markdown files in the repository root (CHANGELOG.md, PRD.md, design.md,
architecture.md, CODE_OF_CONDUCT.md, LICENSE / LICENSE.md) and writes styled pages into
site/ using the same shell (header, menu, footer, theme) as the other site pages, so
the website no longer sends visitors to GitHub to read them.

It also repoints leftover GitHub "blob" links on every page to the local copies,
adds a Changelog link to the navigation, and updates sitemap.xml and vercel.json.

Usage (from the repository root):
    pip install markdown-it-py
    python3 scripts/build_site.py

Re-run it whenever CHANGELOG.md or the other source documents change, then commit the
regenerated files in site/.
"""
from __future__ import annotations

import html
import json
import re
import shutil
from datetime import date
from pathlib import Path

from markdown_it import MarkdownIt

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
ASSETS_SRC = ROOT / "app" / "src" / "main" / "assets"
BASE = "https://my-petal-browser.vercel.app"
GH = "https://github.com/shreyagarwal72/petal"
TODAY = date.today().isoformat()
CSS_MARK = "/* Changelog & documentation pages */"

md = MarkdownIt("commonmark", {"html": True}).enable("table")


# --------------------------------------------------------------------------- helpers
def esc(s: str) -> str:
    return html.escape(s, quote=True)


def slug(s: str) -> str:
    s = re.sub(r"<[^>]+>", "", s).lower()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-") or "section"


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def write(p: Path, s: str) -> None:
    p.write_text(s, encoding="utf-8")


def render(src: str) -> str:
    out = md.render(src)
    out = out.replace("<table>", '<div class="table-wrap"><table>').replace("</table>", "</table></div>")
    return out


# --------------------------------------------------------------------------- shell
def load_shell() -> tuple[str, str]:
    src = read(SITE / "support.html")
    a = src.index('<main id="main-content"')
    b = src.index("</main>") + len("</main>")
    head = re.sub(r'\s+class="active"(\s+aria-current="page")?', "", src[:a])
    return head, src[b:]


def build_head(head: str, title: str, desc: str, path: str, crumbs: list[tuple[str, str]]) -> str:
    full_title = f"{title} — Petal Browser"
    url = f"{BASE}{path}"

    def meta(h: str, attr: str, name: str, value: str) -> str:
        pat = rf'(<meta {attr}="{re.escape(name)}" content=")[^"]*(")'
        return re.sub(pat, lambda m: m.group(1) + esc(value) + m.group(2), h, count=1)

    head = re.sub(r"<title>.*?</title>", lambda m: f"<title>{esc(full_title)}</title>", head, count=1, flags=re.S)
    head = meta(head, "name", "description", desc)
    head = meta(head, "property", "og:url", url)
    head = meta(head, "property", "og:title", full_title)
    head = meta(head, "property", "og:description", desc)
    head = meta(head, "name", "twitter:title", full_title)
    head = meta(head, "name", "twitter:description", desc)
    head = re.sub(r'(<link rel="canonical" href=")[^"]*(")', lambda m: m.group(1) + url + m.group(2), head, count=1)
    items = [{"@type": "ListItem", "position": i + 1, "name": n, "item": u} for i, (n, u) in enumerate(crumbs)]
    ld = json.dumps({"@context": "https://schema.org", "@type": "BreadcrumbList", "itemListElement": items}, ensure_ascii=False)
    head = re.sub(
        r'(<script type="application/ld\+json">).*?(</script>)',
        lambda m: m.group(1) + "\n  " + ld + "\n  " + m.group(2),
        head, count=1, flags=re.S,
    )
    return head


def make_page(path: str, title: str, desc: str, eyebrow: str, lead: str, toc: list[tuple[str, str]],
              body: str, meta_line: str = "", toc_class: str = "toc", nav_active: str | None = None,
              extra_script: str = "") -> None:
    head, tail = load_shell()
    head = build_head(head, title, desc, path, [("Petal Browser", BASE + "/"), (title, BASE + path)])
    if nav_active:
        head = head.replace(f'href="{nav_active}">Changelog</a>', f'href="{nav_active}" class="active" aria-current="page">Changelog</a>', 1)
        head = head.replace(f'href="{nav_active}">Changelog <svg', f'href="{nav_active}" class="active">Changelog <svg', 1)
    toc_html = ""
    if toc:
        lis = "".join(f'<li><a href="#{i}">{esc(t)}</a></li>' for i, t in toc)
        toc_html = f'<aside class="{toc_class}" aria-label="On this page"><p class="toc-title">On this page</p><ol>{lis}</ol></aside>'
    meta_html = f'<p class="page-meta">{esc(meta_line)}</p>' if meta_line else ""
    main = f"""<main id="main-content" class="page">
    <div class="container">
      <nav class="crumbs" aria-label="Breadcrumb"><a href="/">Home</a><span aria-hidden="true">/</span><span aria-current="page">{esc(title)}</span></nav>
      <header class="page-hero">
        <p class="eyebrow">{esc(eyebrow)}</p>
        <h1 class="page-title">{esc(title)}</h1>
        <p class="lead">{esc(lead)}</p>
        {meta_html}
      </header>
      <div class="page-grid{'' if toc else ' page-grid--single'}">
        {toc_html}
        <div class="prose">
{body}
        </div>
      </div>
    </div>
  </main>"""
    if extra_script:
        tail = tail.replace('<script src="/page.js', extra_script + '\n  <script src="/page.js', 1)
    write(SITE / (path.strip("/") + ".html"), head + main + tail)


# --------------------------------------------------------------------------- changelog
def ver_tuple(header: str) -> tuple[int, ...]:
    m = re.match(r"v\s?(\d+(?:\.\d+)*)", header)
    return tuple(int(x) for x in m.group(1).split(".")) if m else (0,)


def split_header(header: str) -> tuple[str, str]:
    header = header.strip().rstrip(":")
    m = re.match(r"^(v\s?\d+(?:\.\d+)*)\s*[—–-]\s*(.+)$", header)
    if m:
        return m.group(1).replace(" ", ""), m.group(2).strip()
    return header, ""


def release_html(header: str, body: str, rid: str, badge: str = "") -> str:
    ver, title = split_header(header)
    body = re.sub(r"\n*-{3,}\s*$", "", body.strip())
    inner = render(body)
    if "<h3" not in inner:
        inner = re.sub(r"<(/?)h4>", r"<\1h3>", inner)
        inner = re.sub(r"<(/?)h5>", r"<\1h4>", inner)
    title_html = f'<span class="rel-title">{html.escape(title)}</span>' if title else ""
    badge_html = f'<span class="rel-badge">{badge}</span>' if badge else ""
    return (
        f'<section class="release" id="{rid}">'
        f'<h2><a class="rel-ver" href="#{rid}">{html.escape(ver)}</a>{badge_html}{title_html}</h2>'
        f"{inner}</section>"
    )


def build_changelog() -> None:
    text = read(ROOT / "CHANGELOG.md")
    chunks = re.split(r"(?m)^### +", text)[1:]
    releases = []
    for c in chunks:
        header, _, body = c.partition("\n")
        releases.append((header.strip(), body))

    # Petal's own history ends where the inherited FOSS Browser history starts ("v 24 (WIP)").
    cut = next(i for i, (h, _) in enumerate(releases) if h.startswith("v 24"))
    petal, upstream = releases[:cut], releases[cut:]

    # One Petal entry (v3.7) was appended at the very end of the file; put it in version order.
    stray = [r for r in upstream if "—" in r[0]]
    upstream = [r for r in upstream if r not in stray]
    for r in stray:
        pos = next((i for i, p in enumerate(petal) if ver_tuple(p[0]) < ver_tuple(r[0])), len(petal))
        petal.insert(pos, r)

    seen: set[str] = set()

    def rid(header: str, prefix: str = "") -> str:
        base = prefix + (slug(split_header(header)[0]) or "release")
        n, out = 1, base
        while out in seen:
            n += 1
            out = f"{base}-{n}"
        seen.add(out)
        return out

    petal_html, toc = [], []
    for i, (h, b) in enumerate(petal):
        r = rid(h)
        toc.append((r, split_header(h)[0]))
        petal_html.append(release_html(h, b, r, badge="Latest" if i == 0 else ""))

    up_html = [release_html(h, b, rid(h, "fb-")) for h, b in upstream]

    latest = split_header(petal[0][0])[0]
    tools = (
        '<div class="changelog-tools">'
        '<label class="sr-only" for="cl-search">Search the changelog</label>'
        '<input id="cl-search" type="search" placeholder="Search releases, e.g. “PDF”, “extensions”, “GeckoView”" autocomplete="off">'
        '<span id="cl-count" class="page-meta" aria-live="polite"></span></div>'
        '<p id="cl-empty" class="no-results" hidden>No releases match your search.</p>'
    )
    upstream_block = (
        '<section id="foss-browser-history" class="upstream">'
        "<h2>Before the fork: FOSS Browser history</h2>"
        "<p>Petal began as a fork of FOSS Browser. These are that project&rsquo;s own release notes, kept for "
        "attribution and completeness. Version numbers here belong to FOSS Browser, not Petal.</p>"
        f'<details class="faq" id="fb-details"><summary>Show {len(up_html)} earlier FOSS Browser releases</summary>'
        f'<div class="faq-a">{"".join(up_html)}</div></details></section>'
    )
    toc.append(("foss-browser-history", "FOSS Browser history"))
    body = tools + "\n".join(petal_html) + "\n" + upstream_block
    make_page(
        "/changelog", "Changelog",
        f"Every Petal Browser release, newest first. Latest: {latest}. Full release notes for the privacy-focused GeckoView Android browser.",
        "Releases", "What changed in every Petal release, newest first.", toc, body,
        meta_line=f"Latest release: {latest} · {len(petal)} Petal releases",
        toc_class="toc toc--versions", nav_active="/changelog",
        extra_script='  <script src="/changelog.js?v=1" defer></script>',
    )
    print(f"changelog: {len(petal)} Petal releases, {len(up_html)} FOSS Browser releases")


# --------------------------------------------------------------------------- docs
DOC_LINKS = {
    "CHANGELOG.md": "/changelog", "PRD.md": "/prd", "design.md": "/design",
    "architecture.md": "/architecture", "CODE_OF_CONDUCT.md": "/code-of-conduct",
}


def sections_from_md(text: str) -> tuple[str, list[tuple[str, str]]]:
    text = re.sub(r"\A\s*# .*\n", "", text, count=1)
    parts = re.split(r"(?m)^## +", text)
    out, toc = [], []
    intro = parts[0].strip()
    if intro:
        out.append(render(re.sub(r"\n*-{3,}\s*$", "", intro)))
    for p in parts[1:]:
        heading, _, body = p.partition("\n")
        heading = heading.strip()
        sid = slug(heading)
        body = re.sub(r"\n*-{3,}\s*$", "", body.strip())
        toc.append((sid, heading))
        out.append(f'<section id="{sid}"><h2>{html.escape(heading)}</h2>\n{render(body)}</section>')
    return "\n".join(out), toc


def build_doc(path: str, src: str, title: str, eyebrow: str, lead: str, desc: str) -> None:
    body, toc = sections_from_md(read(ROOT / src))
    for k, v in DOC_LINKS.items():
        body = body.replace(f'href="{k}"', f'href="{v}"')
    make_page(path, title, desc, eyebrow, lead, toc, body, meta_line=f"Mirrored from {src} in the Petal repository")
    print(f"doc: {path} ({len(toc)} sections)")


def build_gpl() -> None:
    summary = read(ROOT / "LICENSE.md").split("\n---", 1)[0]
    summary = re.sub(r"\A\s*# .*\n", "", summary, count=1).replace("(LICENSE)", "(#full-text)")
    full = read(ROOT / "LICENSE")
    dest = SITE / "assets" / "licenses"
    dest.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / "LICENSE", dest / "GPL-3.0.txt")
    body = (
        render(summary.strip())
        + '<p><a class="btn btn--tonal" href="/assets/licenses/GPL-3.0.txt" download>Download as text</a> '
        '<a class="btn btn--tonal" href="/licenses">Third-party licenses</a></p>'
        f'<section id="full-text"><h2>Full license text</h2><pre class="license-text">{html.escape(full)}</pre></section>'
    )
    make_page(
        "/gpl", "GNU GPL v3.0", "The full GNU General Public License v3.0 text that Petal Browser is released under.",
        "Legal", "Petal is free software. This is the license it is distributed under.",
        [("full-text", "Full license text")], body,
    )
    print("doc: /gpl")


# --------------------------------------------------------------------------- site-wide fixes
def copy_license_asset(name: str) -> None:
    src = ASSETS_SRC / name
    if src.exists():
        dest = SITE / "assets" / "licenses"
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dest / name)
    else:
        print(f"WARN: missing license asset {name}")


def fix_links(h: str, name: str) -> str:
    internal = {
        f"{GH}/blob/main/CHANGELOG.md": "/changelog",
        f"{GH}/blob/main/PRD.md": "/prd",
        f"{GH}/blob/main/design.md": "/design",
        f"{GH}/blob/main/architecture.md": "/architecture",
        f"{GH}/blob/main/LICENSE.md": "/gpl",
        f"{GH}/blob/main/LICENSE": "/gpl",
        f"{GH}/blob/main/CODE_OF_CONDUCT.md": "/code-of-conduct",
    }
    for url, dst in internal.items():
        h = re.sub(rf'href="{re.escape(url)}"(\s+target="_blank"\s+rel="noopener noreferrer")?', f'href="{dst}"', h)

    def asset(m: re.Match) -> str:
        copy_license_asset(m.group(1))
        return f'href="/assets/licenses/{m.group(1)}"'

    h = re.sub(rf'href="{re.escape(GH)}/blob/main/app/src/main/assets/([\w.\-]+)"(\s+target="_blank"\s+rel="noopener noreferrer")?', asset, h)
    h = h.replace(">PRD.md</a>", ">Product requirements</a>")
    h = h.replace("pages.css?v=157.0.3", "pages.css?v=157.0.4")

    pre = h.split("<main", 1)[0]
    if 'href="/changelog"' not in pre:
        pat = r'<li[^>]*>\s*<a href="/faq"[^>]*>.*?</a>\s*</li>'
        parts = h.split("<main", 1)
        head = parts[0]
        found = 0

        def add(m: re.Match) -> str:
            nonlocal found
            found += 1
            item = m.group(0).replace('href="/faq"', 'href="/changelog"').replace("FAQ", "Changelog")
            item = re.sub(r'\s+class="active"', "", item)
            item = re.sub(r'\s+aria-current="page"', "", item)
            return m.group(0) + item

        head = re.sub(pat, add, head, flags=re.S)
        if found < 2:
            print(f"WARN: {name}: added Changelog to {found}/2 nav lists")

        def renum(m: re.Match) -> str:
            n = iter(range(100))
            return re.sub(r"--i:\d+", lambda _: f"--i:{next(n)}", m.group(0))

        head = re.sub(r'<ul class="menu-list".*?</ul>', renum, head, count=1, flags=re.S)
        h = head + ("<main" + parts[1] if len(parts) > 1 else "")

    if 'href="/code-of-conduct"' not in h:
        h = re.sub(
            r'(<li><a href="/licenses">Open-source licenses</a></li>)',
            r'\1\n              <li><a href="/gpl">GNU GPL v3.0 (full text)</a></li>\n              <li><a href="/code-of-conduct">Code of conduct</a></li>',
            h, count=1,
        )
    return h


def update_sitemap() -> None:
    p = SITE / "sitemap.xml"
    s = read(p)
    new = [("/changelog", "weekly", "0.7"), ("/architecture", "monthly", "0.4"), ("/design", "monthly", "0.4"),
           ("/prd", "monthly", "0.4"), ("/code-of-conduct", "yearly", "0.3"), ("/gpl", "yearly", "0.3")]
    add = ""
    for path, freq, prio in new:
        if f"<loc>{BASE}{path}</loc>" not in s:
            add += f"  <url>\n    <loc>{BASE}{path}</loc>\n    <lastmod>{TODAY}</lastmod>\n    <changefreq>{freq}</changefreq>\n    <priority>{prio}</priority>\n  </url>\n"
    if add:
        s = s.replace("</urlset>", add + "</urlset>")
    s = re.sub(r"(<lastmod>)[^<]*(</lastmod>)", lambda m: m.group(1) + TODAY + m.group(2), s) if add else s
    write(p, s)


def update_vercel() -> None:
    p = ROOT / "vercel.json"
    cfg = json.loads(read(p))
    for rule in cfg["rewrites"]:
        if rule["source"].startswith("/(privacy|"):
            rule["source"] = "/(privacy|terms|faq|support|download|licenses|changelog|prd|design|architecture|code-of-conduct|gpl)"
        elif "page\\.js" in rule["source"] and "changelog\\.js" not in rule["source"]:
            rule["source"] = rule["source"].replace("page\\.js|", "page\\.js|changelog\\.js|")
    write(p, json.dumps(cfg, indent=2) + "\n")


CSS = f"""
{CSS_MARK}
.sr-only {{ position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }}
.changelog-tools {{ display: flex; flex-wrap: wrap; align-items: center; gap: 8px 16px; margin-bottom: 24px; }}
.changelog-tools input {{ flex: 1 1 260px; min-height: 52px; padding: 0 22px; border: 0; border-radius: 999px; background: var(--surface-container); color: var(--on-surface); font: inherit; box-shadow: inset 0 0 0 1px var(--outline-variant); }}
.changelog-tools input:focus-visible {{ outline: 3px solid var(--primary); outline-offset: 2px; }}
.no-results {{ padding: 24px; border-radius: 24px; background: var(--surface-container); text-align: center; }}
.prose .release {{ margin-bottom: 16px; padding: clamp(18px, 3vw, 28px); border-radius: 28px; background: var(--surface-container); scroll-margin-top: 96px; }}
.prose .release[hidden], .toc li[hidden] {{ display: none; }}
.prose .release h2 {{ display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; font-size: clamp(1.25rem, 1.4vw + .95rem, 1.625rem); margin-bottom: 12px; }}
.prose .release h3 {{ margin: 18px 0 8px; font-size: 1.0625rem; }}
.prose .release ul ul {{ margin-top: 8px; margin-bottom: 0; }}
.prose .release p:last-child, .prose .release ul:last-child {{ margin-bottom: 0; }}
.prose .release blockquote {{ margin: 0 0 14px; padding: 2px 0 2px 16px; border-left: 4px solid var(--primary); color: var(--on-surface-variant); }}
.prose .release blockquote p {{ margin: 0; }}
.prose .release .rel-ver {{ padding: 4px 14px; border-radius: 999px; background: var(--primary); color: var(--on-primary); font-size: .9375rem; font-weight: 750; text-decoration: none; }}
.rel-badge {{ padding: 4px 12px; border-radius: 999px; background: var(--secondary-container); color: var(--on-secondary-container); font-size: .8125rem; font-weight: 700; }}
.rel-title {{ flex: 1 1 100%; }}
.toc--versions ol {{ max-height: min(62vh, 560px); overflow-y: auto; overscroll-behavior: contain; }}
.prose .upstream details.faq .faq-a {{ padding: 8px 12px 16px; }}
.prose .upstream details.faq .release {{ background: var(--surface); }}
.table-wrap {{ overflow-x: auto; margin: 0 0 16px; border-radius: 20px; background: var(--surface-container); }}
.license-text {{ max-height: 70vh; overflow: auto; padding: 20px; border-radius: 20px; background: var(--surface-container); font-family: var(--font-code); font-size: .8125rem; line-height: 1.55; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--on-surface); }}
"""

JS = """/* Petal changelog: live search over releases, and opens the FOSS Browser history when a link points into it. */
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
"""


# --------------------------------------------------------------------------- main
def main() -> None:
    for f in sorted(SITE.glob("*.html")):
        if f.name.startswith("google"):
            continue
        write(f, fix_links(read(f), f.name))

    build_changelog()
    build_doc("/prd", "PRD.md", "Product requirements", "Docs",
              "What Petal is, who it is for and the platform constraints it is built around.",
              "Petal Browser product requirements: positioning, supported Android versions, features and quality targets.")
    build_doc("/design", "design.md", "Design system", "Docs",
              "The Material 3 Expressive shapes, colour, type and motion behind Petal's interface.",
              "Petal Browser design system: Material 3 Expressive shapes, colour, typography and motion.")
    build_doc("/architecture", "architecture.md", "GeckoView architecture", "Docs",
              "How Petal combines Mozilla GeckoView with a Jetpack Compose interface.",
              "Petal Browser architecture: the GeckoView engine core, Jetpack Compose UI layers and extension pipeline.")
    build_doc("/code-of-conduct", "CODE_OF_CONDUCT.md", "Code of conduct", "Community",
              "How we expect people to behave in the Petal community.",
              "Petal Browser community code of conduct (Contributor Covenant).")
    build_gpl()

    for f in sorted(SITE.glob("*.html")):
        if f.name.startswith("google"):
            continue
        write(f, fix_links(read(f), f.name))

    css = read(SITE / "pages.css")
    if CSS_MARK not in css:
        write(SITE / "pages.css", css.rstrip("\n") + "\n" + CSS)
    write(SITE / "changelog.js", JS)
    update_sitemap()
    update_vercel()
    print("done")


if __name__ == "__main__":
    main()
