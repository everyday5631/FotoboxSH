#!/usr/bin/env python3
"""Baut die GitHub-Pages-Seite aus README.md und docs/**/*.md.

Aufruf:  python tools/build_site.py [Zielordner]      (Standard: _site)
Nötig:   pip install markdown-it-py

Regeln (stehen auch in CLAUDE.md und docs/ENTWICKLUNG.md):
- Jede Seite ist eine Markdown-Datei in docs/ mit Kopfblock (Front Matter):

      ---
      title: Titel in der Navigation
      category: bedienung        # Pflicht, eine ID aus docs/_site.json
      order: 20                  # optional, kleiner = weiter oben (Standard 100)
      summary: Ein Satz         # optional, erscheint auf der Startseite
      ---

- Kategorien stehen in docs/_site.json. Neue Kategorie = Eintrag dort ergänzen.
- Ohne gültige Kategorie bricht der Bau ab (der Fehler erscheint im Pull Request).
- Bilder, PDFs usw. neben den Seiten werden unverändert mitkopiert und von der Seite aus verlinkt.
- Dateien und Ordner, die mit «_» beginnen, werden nicht veröffentlicht.
- Text zwischen  <!-- intern -->  und  <!-- /intern -->  bleibt im Repository, erscheint aber nicht auf der Website
  (für Zugangsdaten u. Ä.).
"""
import html
import json
import os
import re
import shutil
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

from markdown_it import MarkdownIt

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
REPO = os.environ.get("GITHUB_REPOSITORY", "everyday5631/FotoboxSH")
BRANCH = "main"

errors: list[str] = []
warnings: list[str] = []


def err(path, msg):
    errors.append(f"{path}: {msg}")
    print(f"::error file={path}::{msg}")


def warn(path, msg):
    warnings.append(f"{path}: {msg}")
    print(f"::warning file={path}::{msg}")


# ---------------------------------------------------------------- Einlesen

def parse_front_matter(text):
    """Gibt (Felder, Rest) zurück. Einfaches «schluessel: wert», keine YAML-Bibliothek nötig."""
    m = re.match(r"\A---\r?\n(.*?)\r?\n---\r?\n?", text, re.S)
    if not m:
        return {}, text
    fields = {}
    for line in m.group(1).splitlines():
        line = line.split(" #", 1)[0].rstrip() if " #" in line else line.rstrip()
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, sep, value = line.partition(":")
        if sep:
            fields[key.strip()] = value.strip().strip("\"'")
    return fields, text[m.end():]


def strip_intern(text):
    return re.sub(r"<!--\s*intern\s*-->.*?<!--\s*/intern\s*-->", "[nur im Repository]", text, flags=re.S)


def first_heading(text):
    m = re.search(r"^#\s+(.+?)\s*$", text, re.M)
    return m.group(1).strip() if m else None


# ---------------------------------------------------------------- HTML

def slugify(text, used):
    text = re.sub(r"<[^>]+>", "", html.unescape(text)).strip().lower()
    slug = re.sub(r"[^\w\- ]", "", text).replace(" ", "-")
    base, n = slug, 0
    while slug in used:
        n += 1
        slug = f"{base}-{n}"
    used.add(slug)
    return slug


def add_heading_ids(body):
    used: set[str] = set()

    def repl(m):
        level, inner = m.group(1), m.group(2)
        sid = slugify(inner, used)
        return f'<h{level} id="{sid}">{inner}</h{level}>'

    return re.sub(r"<h([1-6])>(.*?)</h\1>", repl, body, flags=re.S)


def wrap_tables(body):
    return body.replace("<table>", '<div class="tabelle"><table>').replace("</table>", "</table></div>")


CSS = """
:root{--bg:#faf9f5;--fg:#1f2933;--muted:#5b6770;--navy:#233446;--gold:#efbb50;--line:#dcd8cc;--card:#fff;--code:#f1efe7;--link:#1f5a99}
@media(prefers-color-scheme:dark){:root{--bg:#16202a;--fg:#e6e9ec;--muted:#9aa7b2;--line:#2f3d4b;--card:#1d2a36;--code:#223140;--link:#8ec1f5}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.6 Arial,Helvetica,sans-serif}
a{color:var(--link)}
header.top{background:var(--navy);color:#fff;padding:.9rem 1.2rem;border-bottom:4px solid var(--gold)}
header.top a{color:#fff;text-decoration:none;font-weight:bold;font-size:1.15rem}
header.top span{color:var(--gold);margin-left:.6rem;font-size:.95rem}
.layout{display:flex;gap:2rem;max-width:1180px;margin:0 auto;padding:1.2rem}
nav.seiten{flex:0 0 250px;font-size:.93rem}
nav.seiten h2{font-size:.78rem;text-transform:uppercase;letter-spacing:.06em;color:var(--muted);margin:1.2rem 0 .3rem}
nav.seiten ul{list-style:none;margin:0;padding:0}
nav.seiten li a{display:block;padding:.28rem .6rem;border-radius:6px;text-decoration:none;color:var(--fg)}
nav.seiten li a:hover{background:var(--code)}
nav.seiten li a.aktuell{background:var(--navy);color:#fff}
nav.seiten summary{display:none}
main{flex:1 1 auto;min-width:0;background:var(--card);border:1px solid var(--line);border-radius:10px;padding:1.2rem 1.8rem 2rem}
main img{max-width:100%;height:auto}
main h1{margin-top:.4rem}main h1,main h2,main h3{line-height:1.25}
main pre,main code{background:var(--code);border-radius:5px}
main code{padding:.1em .35em;font-size:.92em}main pre{padding:.8rem 1rem;overflow-x:auto}main pre code{padding:0}
main blockquote{margin:1rem 0;padding:.2rem 1rem;border-left:4px solid var(--gold);background:var(--code)}
.tabelle{overflow-x:auto}table{border-collapse:collapse;margin:1rem 0}th,td{border:1px solid var(--line);padding:.4rem .7rem;vertical-align:top;text-align:left}th{background:var(--code)}
.uebersicht{margin-top:2.5rem;border-top:2px solid var(--gold);padding-top:1rem}
.uebersicht .kat{margin:1.2rem 0}.uebersicht .kat p{margin:.2rem 0;color:var(--muted)}
footer{max-width:1180px;margin:0 auto;padding:0 1.2rem 2rem;color:var(--muted);font-size:.85rem}
@media(max-width:820px){.layout{display:block;padding:.7rem}nav.seiten{margin-bottom:1rem}nav.seiten summary{display:block;cursor:pointer;font-weight:bold;padding:.5rem .2rem}main{padding:1rem}}
"""


def page_html(site, page, body, pages_by_cat, rel_root, overview=""):
    def nav():
        out = [f'<nav class="seiten"><details open><summary>Seiten</summary>']
        cls = ' class="aktuell"' if page["out"] == "index.html" else ""
        out.append(f'<ul><li><a{cls} href="{rel_root}index.html">Startseite</a></li></ul>')
        for cat in site["kategorien"]:
            items = pages_by_cat.get(cat["id"], [])
            if not items:
                continue
            out.append(f'<h2>{html.escape(cat["titel"])}</h2><ul>')
            for p in items:
                cls = ' class="aktuell"' if p["out"] == page["out"] else ""
                out.append(f'<li><a{cls} href="{rel_root}{p["out"]}">{html.escape(p["title"])}</a></li>')
            out.append("</ul>")
        out.append("</details></nav>")
        return "".join(out)

    title = page["title"] if page["out"] != "index.html" else site["titel"]
    return f"""<!DOCTYPE html>
<html lang="de-CH">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)} – {html.escape(site["titel"])}</title>
<style>{CSS}</style>
</head>
<body>
<header class="top"><a href="{rel_root}index.html">{html.escape(site["titel"])}</a><span>{html.escape(site["untertitel"])}</span></header>
<div class="layout">
{nav()}
<main>
{body}
{overview}
</main>
</div>
<footer>Automatisch aus dem Repository erzeugt, Quelle: <a href="https://github.com/{REPO}/blob/{BRANCH}/{page["src"]}">{page["src"]}</a></footer>
</body>
</html>
"""


# ---------------------------------------------------------------- Links

def rewrite_links(body, page, md_targets, published_files):
    """Relative Links umbiegen: .md -> .html, Dateien ausserhalb der Seite -> GitHub."""
    src_dir = (ROOT / page["src"]).parent
    out_dir = Path(page["out"]).parent

    def repl(m):
        attr, quote, url = m.group(1), m.group(2), m.group(3)
        parts = urlsplit(html.unescape(url))
        if parts.scheme or parts.netloc or url.startswith(("#", "mailto:", "tel:", "data:")) or not parts.path:
            return m.group(0)
        target = (src_dir / unquote(parts.path)).resolve()
        try:
            rel_repo = target.relative_to(ROOT).as_posix()
        except ValueError:
            warn(page["src"], f"Link zeigt ausserhalb des Repositorys: {url}")
            return m.group(0)
        frag = f"#{parts.fragment}" if parts.fragment else ""
        if rel_repo in md_targets:
            new = os.path.relpath(md_targets[rel_repo], out_dir or ".").replace(os.sep, "/") + frag
        elif rel_repo in published_files:
            return m.group(0)
        elif target.is_file():
            new = f"https://github.com/{REPO}/blob/{BRANCH}/{rel_repo}{frag}"
        elif target.is_dir():
            new = f"https://github.com/{REPO}/tree/{BRANCH}/{rel_repo}{frag}"
        else:
            warn(page["src"], f"Link ins Leere: {url}")
            return m.group(0)
        return f"{attr}={quote}{html.escape(new, quote=True)}{quote}"

    return re.sub(r'\b(href|src)=(["\'])(.*?)\2', repl, body)


# ---------------------------------------------------------------- Hauptprogramm

def main():
    out_root = Path(sys.argv[1] if len(sys.argv) > 1 else "_site")
    if not out_root.is_absolute():
        out_root = ROOT / out_root

    site = json.loads((DOCS / "_site.json").read_text(encoding="utf-8"))
    cat_ids = {c["id"] for c in site["kategorien"]}

    # Quellen sammeln
    sources = [ROOT / "README.md"] + sorted(
        p for p in DOCS.rglob("*.md")
        if not any(part.startswith("_") for part in p.relative_to(DOCS).parts)
    )
    pages = []
    for src in sources:
        rel = src.relative_to(ROOT).as_posix()
        text = src.read_text(encoding="utf-8")
        fields, rest = parse_front_matter(text)
        is_home = src == ROOT / "README.md"
        title = fields.get("title") or first_heading(rest) or src.stem
        if is_home:
            out = "index.html"
        else:
            out = rel[:-3] + ".html"
            cat = fields.get("category")
            if not cat:
                err(rel, "Kopfblock mit «category:» fehlt. Oben in die Datei einfügen:\n---\ntitle: ...\ncategory: "
                         + "|".join(sorted(cat_ids)) + "\n---\n(Kategorien: docs/_site.json)")
                continue
            if cat not in cat_ids:
                err(rel, f"Unbekannte Kategorie «{cat}». Erlaubt: {', '.join(sorted(cat_ids))}. "
                         "Neue Kategorie in docs/_site.json eintragen.")
                continue
        try:
            order = int(fields.get("order", 100))
        except ValueError:
            err(rel, f"«order» muss eine Zahl sein, steht aber «{fields.get('order')}».")
            continue
        pages.append({"src": rel, "out": out, "title": title, "category": fields.get("category"),
                      "order": order, "summary": fields.get("summary", ""), "body_md": strip_intern(rest),
                      "home": is_home})

    if errors:
        print(f"\n{len(errors)} Fehler, Seite nicht gebaut.", file=sys.stderr)
        sys.exit(1)

    pages_by_cat = {c["id"]: sorted((p for p in pages if p["category"] == c["id"]),
                                    key=lambda p: (p["order"], p["title"].lower()))
                    for c in site["kategorien"]}
    md_targets = {p["src"]: p["out"] for p in pages}

    # Unveränderte Dateien (Bilder, PDF, HTML ...) aus docs/ mitnehmen
    if out_root.exists():
        shutil.rmtree(out_root)
    published_files = set()
    for f in DOCS.rglob("*"):
        rel = f.relative_to(ROOT)
        if f.is_dir() or f.suffix == ".md" or any(part.startswith("_") for part in f.relative_to(DOCS).parts):
            continue
        dest = out_root / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(f, dest)
        published_files.add(rel.as_posix())
    (out_root / ".nojekyll").write_text("", encoding="utf-8")

    md = MarkdownIt("commonmark", {"html": True}).enable("table").enable("strikethrough")

    overview = ['<div class="uebersicht"><h2 id="alle-seiten">Alle Seiten</h2>']
    for cat in site["kategorien"]:
        items = pages_by_cat[cat["id"]]
        if not items:
            continue
        overview.append(f'<div class="kat"><h3>{html.escape(cat["titel"])}</h3><p>{html.escape(cat.get("beschreibung", ""))}</p><ul>')
        for p in items:
            s = f" – {html.escape(p['summary'])}" if p["summary"] else ""
            overview.append(f'<li><a href="{p["out"]}">{html.escape(p["title"])}</a>{s}</li>')
        overview.append("</ul></div>")
    overview.append("</div>")

    for page in pages:
        body = md.render(page["body_md"])
        body = add_heading_ids(wrap_tables(body))
        body = rewrite_links(body, page, md_targets, published_files)
        depth = len(Path(page["out"]).parts) - 1
        rel_root = "../" * depth
        extra = ""
        if page["home"]:
            # Pfade in der Übersicht sind relativ zur Startseite; passt, weil sie nur dort steht.
            extra = "".join(overview)
        dest = out_root / page["out"]
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(page_html(site, page, body, pages_by_cat, rel_root, extra), encoding="utf-8")

    print(f"{len(pages)} Seiten, {len(published_files)} Dateien nach {out_root} geschrieben, {len(warnings)} Warnungen.")
    for c in site["kategorien"]:
        print(f"  {c['titel']}: " + ", ".join(p["src"] for p in pages_by_cat[c["id"]]))


if __name__ == "__main__":
    main()
