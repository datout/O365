from pathlib import Path
import re

def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")

def write(p: Path, s: str) -> None:
    p.write_text(s, encoding="utf-8")

def patch_workflow():
    wf = Path(".github/workflows/docker-publish.yml")
    if not wf.exists():
        print("[SKIP] workflow not found:", wf)
        return

    lines = read(wf).splitlines(True)

    start = None
    for i, line in enumerate(lines):
        # top-level on:
        if re.match(r"^on:\s*", line) and not line.startswith((" ", "\t")):
            start = i
            break

    if start is None:
        print("[WARN] top-level 'on:' not found, skip workflow patch")
        return

    end = start + 1
    # eat indented block + blank + comments until next top-level key
    while end < len(lines):
        l = lines[end]
        if re.match(r"^[ \t]", l) or re.match(r"^\s*$", l) or re.match(r"^\s*#", l):
            end += 1
            continue
        break

    new = lines[:start] + ["on:\n", "  workflow_dispatch:\n"] + lines[end:]
    write(wf, "".join(new))
    print("[OK] workflow -> manual only (workflow_dispatch)")

def ensure_css():
    css = Path("src/main/resources/static/css/refer_github.css")
    if not css.exists():
        print("[SKIP] CSS not found:", css)
        return

    marker = "/* password visibility toggle (register/refer pages) */"
    txt = read(css)
    if marker in txt:
        print("[SKIP] CSS already patched")
        return

    block = """
/* password visibility toggle (register/refer pages) */
.gh-input-group{ position:relative; width:100%; }
.gh-input-group>.gh-input{ padding-right:44px; }
.gh-pw-toggle{
  position:absolute; right:10px; top:50%; transform:translateY(-50%);
  border:0; background:transparent; padding:6px; border-radius:8px;
  cursor:pointer; color:var(--fgColor-muted,#57606a); opacity:.9;
}
.gh-pw-toggle:hover{ background:rgba(0,0,0,.06); color:var(--fgColor-default,#24292f); }
.gh-pw-toggle svg{ width:16px; height:16px; display:block; }
.gh-pw-toggle .icon-eye-off{ display:none; }
.gh-pw-toggle.is-on .icon-eye{ display:none; }
.gh-pw-toggle.is-on .icon-eye-off{ display:block; }
"""
    write(css, txt.rstrip() + "\n" + block.lstrip() + "\n")
    print("[OK] CSS appended")

def ensure_js():
    js = Path("src/main/resources/static/js/pw_toggle.js")
    js.parent.mkdir(parents=True, exist_ok=True)
    code = """(function () {
  function init() {
    document.querySelectorAll(".gh-pw-toggle").forEach(function (btn) {
      btn.addEventListener("mousedown", function (e) { e.preventDefault(); });
      btn.addEventListener("click", function () {
        var id = btn.getAttribute("data-target");
        var input = document.getElementById(id);
        if (!input) return;
        var show = input.type === "password";
        input.type = show ? "text" : "password";
        btn.classList.toggle("is-on", show);
        btn.setAttribute("aria-label", show ? "隐藏密码" : "显示密码");
        btn.setAttribute("title", show ? "隐藏密码" : "显示密码");
      });
    });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();"""
    if js.exists() and read(js) == code:
        print("[SKIP] JS already up-to-date")
        return
    write(js, code)
    print("[OK] JS written:", js)

def button_html(target: str) -> str:
    return f'''  <button class="gh-pw-toggle" type="button" data-target="{target}" aria-label="显示密码" title="显示密码">
    <svg class="icon-eye" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8S1 12 1 12z"></path>
      <circle cx="12" cy="12" r="3"></circle>
    </svg>
    <svg class="icon-eye-off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      <path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a21.8 21.8 0 0 1 5.06-6.88"></path>
      <path d="M1 1l22 22"></path>
      <path d="M10.58 10.58A3 3 0 0 0 12 15a3 3 0 0 0 2.12-.88"></path>
    </svg>
  </button>'''

def patch_template(path: Path, ids):
    if not path.exists():
        return
    html = read(path)
    changed = False

    # wrap inputs
    for _id in ids:
        if f'data-target="{_id}"' in html:
            continue
        m = re.search(rf'(<input\b[^>]*\bid="{re.escape(_id)}"\b[^>]*>)', html, flags=re.I)
        if not m:
            print(f"[WARN] cannot find input id={_id} in {path}")
            continue
        input_tag = m.group(1)
        wrapped = '<div class="gh-input-group">\\n  ' + input_tag + '\\n' + button_html(_id) + '\\n</div>'
        html = html[:m.start(1)] + wrapped + html[m.end(1):]
        changed = True

    # include js once
    if "pw_toggle.js" not in html:
        if re.search(r'</body\s*>', html, flags=re.I):
            html = re.sub(r'(?i)</body\s*>', '  <script src="/js/pw_toggle.js"></script>\\n</body>', html, count=1)
        else:
            html += '\\n<script src="/js/pw_toggle.js"></script>\\n'
        changed = True

    if changed:
        write(path, html)
        print("[OK] patched:", path)
    else:
        print("[SKIP] already patched:", path)

def main():
    patch_workflow()
    ensure_css()
    ensure_js()
    patch_template(Path("src/main/resources/templates/reg.html"),   ["pwd", "rpwd"])
    patch_template(Path("src/main/resources/templates/refer.html"), ["password", "password2"])

main()
