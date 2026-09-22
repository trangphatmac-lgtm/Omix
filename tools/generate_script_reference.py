#!/usr/bin/env python3
"""Generate source-backed script SDK index and self-contained offline documentation."""
import html, json, re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/script'
JAVA = ROOT / 'src/main/java'
def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or path.read_text(encoding='utf-8') != text:
        path.write_text(text, encoding='utf-8')
def json_text(value): return json.dumps(value, ensure_ascii=False, indent=2) + '\n'
entries = []
for base in ['cn/omix/script/api', 'cn/omix/event/impl', 'cn/omix/module/value/impl', 'cn/omix/management', 'cn/omix/fisproxy', 'cn/omix/util']:
    for path in sorted((JAVA / base).rglob('*.java')):
        if '/util/script/' in str(path): continue
        source = path.read_text(encoding='utf-8')
        package = re.search(r'package\s+([\w.]+)', source).group(1)
        owner = package + '.' + path.stem
        masked = re.sub(r'/\*.*?\*/|//[^\n]*|""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', lambda m: ''.join('\n' if c=='\n' else ' ' for c in m.group()), source, flags=re.S)
        depth, pending, stack, ranges = 0, None, [], []
        for token in re.finditer(r'\b(?:class|interface|enum|record)\s+(\w+)|[{}]',masked):
            if token.group(1): pending = (token.group(1), token.start())
            elif token.group() == '{':
                depth += 1
                if pending: stack.append((depth,*pending));pending=None
            else:
                if stack and stack[-1][0] == depth:
                    _, name, start = stack.pop(); ranges.append((start,token.end(),name))
                depth -= 1
        def declaring(position):
            names = [name for start,end,name in sorted(ranges) if start <= position <= end]
            return package + '.' + '.'.join(names or [path.stem])
        entries.append(dict(owner=owner, name=path.stem, signature=owner, kind='type', source=str(path.relative_to(ROOT))))
        # Declarations only: include constructors and explicitly declared public methods/fields.
        for match in re.finditer(r'\bpublic\s+(?:(?:static|final|abstract|synchronized|volatile)\s+)*(?:<[^;{}]+>\s+)?[\w.$<>?, \[\]]+(?:\([^{};]*?\))?(?=\s*(?:throws\s+[\w., ]+)?\s*[{;=])', source):
            signature = ' '.join(match.group().split())
            if '@UtilityClass' in source and not re.match(r'public (?:static|class|final class)', signature): signature = signature.replace('public ', 'public static ', 1)
            name_match = re.search(r'(\w+)\s*(?:\(|$)', signature)
            if not name_match: continue
            entries.append(dict(owner=declaring(match.start()), name=name_match.group(1), signature=signature, kind='declaration', source=str(path.relative_to(ROOT)), line=source[:match.start()].count('\n')+1))
        # Lombok event accessors are part of the public compiled API.
        if '/event/impl/' in str(path) and '@Getter' in source:
            for field in re.finditer(r'private\s+(final\s+)?([\w.<>?\[\]]+)\s+(\w+)\s*;', source):
                final, type_, name = field.groups()
                cap = name[0].upper()+name[1:]
                entries.append(dict(owner=owner, name=('is' if type_=='boolean' else 'get')+cap, signature=f"public {type_} {'is' if type_=='boolean' else 'get'}{cap}()", kind='generated-accessor', source=str(path.relative_to(ROOT))))
                if not final and '@Setter' in source:
                    entries.append(dict(owner=owner, name='set'+cap, signature=f'public void set{cap}({type_} {name})', kind='generated-accessor', source=str(path.relative_to(ROOT))))
write(DOC/'api.json', json_text(entries))
write(DOC/'sdk.md', '# SDK 声明索引\n\n此文件由实际 Java 声明生成。`api.json` 为相同索引的机器格式；Lombok 事件字段的访问器单独标记。完整原生类型继承关系请以当前依赖和编译检查为准。\n\n'+'\n'.join(f"- `{e['owner']}` — `{e['signature']}`" for e in entries))
# Schema names must match the shared Java dispatcher, no transport-specific shadow tools.
tools = json.loads((DOC/'tools.json').read_text())
names = {tool['function']['name'] for tool in tools}
source = (JAVA/'cn/omix/util/script/ScriptTools.java').read_text()
dispatched = set(re.findall(r'case "(script_\w+)"\s*->', source))
assert names == dispatched, f'Tool schema mismatch: {names ^ dispatched}'
write(DOC/'tools.md', '# 开发工具\n\nHarness、Script Studio 和 MCP 共用以下 schema。编译和加载返回 job，必须轮询 `script_job` 到终态；只有 `loaded` 和实际 generation 表示应用成功。\n\n'+ '\n\n'.join('## '+t['function']['name']+'\n\n'+t['function']['description']+'\n\n```json\n'+json_text(t['function']['parameters']).rstrip()+'\n```' for t in tools))
examples = sorted(p.stem for p in (DOC/'examples').glob('*.java'))
write(DOC/'examples/index.json', json_text(examples))
# Explicit per-module mode host catalog, synchronized with ModeHost.selectorName.
modes=[]
registered = set(re.findall(r'new (\w+)\(\)', (JAVA/'cn/omix/module/ModuleManager.java').read_text()))
for path in sorted((JAVA/'cn/omix/module/impl').rglob('*.java')):
    source=path.read_text()
    if path.stem not in registered: continue
    name=path.stem
    selector={'AutoTool':'Implementation','ChestESP':'Implementation','Chams':'Render Mode'}.get(name, 'Mode' if re.search(r'new\s+ModeValue\(\s*"Mode"',source) else 'Behavior')
    modes.append(dict(module=name,selector=selector,source=str(path.relative_to(ROOT))))
write(DOC/'mode-hosts.json',json_text(modes))
# Export a directly installable skill: references travel with the SKILL.md.
write(DOC/'rotation-manager.md', (ROOT/'docs/rotation-manager.md').read_text())
write(DOC/'game-tools.md', (ROOT/'docs/ai-tools.md').read_text())
for name in ['README.md','common-knowledge.md','rotation-manager.md','game-tools.md','lifecycle.md','api-guide.md','modes.md','tools.md','mcp.md','api.json','mode-hosts.json']:
    write(DOC/'omix-script/references'/name,(DOC/name).read_text())
for path in (DOC/'examples').glob('*'):
    write(DOC/'omix-script/examples'/path.name,path.read_text())
def inline(text):
    value = html.escape(text)
    value = re.sub(r'`([^`]+)`', r'<code>\1</code>', value)
    value = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', value)
    def link(match):
        label, target = match.groups()
        if not target.startswith(('https://', 'http://')):
            target = '#' + ('sdk' if Path(target).name == 'api.json' else Path(target).stem)
        return '<a href="' + target + '">' + label + '</a>'
    return re.sub(r'\[([^\]]+)\]\(([^)]+)\)', link, value)

def markdown(text):
    rendered, code, listing, table = [], None, None, False
    def close_blocks():
        nonlocal listing, table
        if listing: rendered.append('</'+listing+'>'); listing=None
        if table: rendered.append('</tbody></table>'); table=False
    for line in text.splitlines():
        if line.startswith('```'):
            if code is None: close_blocks(); code=[]
            else: rendered.append('<pre><code>'+html.escape('\n'.join(code))+'</code></pre>');code=None
            continue
        if code is not None: code.append(line);continue
        if line.startswith('|'):
            if re.match(r'^\|[ :|\-]+$', line): continue
            cells=[inline(cell.strip()) for cell in line.strip('|').split('|')]
            if not table:
                close_blocks();rendered.append('<table><thead><tr>'+''.join('<th>'+cell+'</th>' for cell in cells)+'</tr></thead><tbody>');table=True
            else: rendered.append('<tr>'+''.join('<td>'+cell+'</td>' for cell in cells)+'</tr>')
            continue
        heading=re.match(r'^(#{1,6}) (.+)',line)
        item=re.match(r'^(?:([-*])|\d+\.) (.+)',line)
        if heading:
            close_blocks();level=min(6,len(heading[1])+1);rendered.append(f'<h{level}>{inline(heading[2])}</h{level}>')
        elif item:
            kind='ul' if item[1] else 'ol'
            if listing!=kind: close_blocks();rendered.append('<'+kind+'>');listing=kind
            rendered.append('<li>'+inline(item[2])+'</li>')
        elif line.strip(): close_blocks();rendered.append('<p>'+inline(line)+'</p>')
        else: close_blocks()
    close_blocks()
    return ''.join(rendered)

sections, navigation = [], []
for path in sorted(DOC.glob('*.md'), key=lambda p: (p.name != 'README.md',p.name)):
    title=path.read_text().splitlines()[0].lstrip('# ')
    navigation.append(f'<a href="#{path.stem}">{html.escape(title)}</a>')
    sections.append(f'<section id="{html.escape(path.stem)}"><div class="filename">{path.name}</div>{markdown(path.read_text())}</section>')
for path in sorted((DOC/'examples').glob('*.java')):
    navigation.append(f'<a href="#{path.stem}">{path.name}</a>')
    sections.append(f'<section id="{path.stem}"><div class="filename">EXAMPLE</div><h2>{path.name}</h2><pre><code>{html.escape(path.read_text())}</code></pre></section>')
page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Omix Java Script SDK</title><style>
:root{color-scheme:dark}*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:95px}body{margin:0;background:#10141c;color:#cbd5e4;font:15px/1.8 system-ui}header{position:sticky;top:0;z-index:2;background:#181e29;border-bottom:1px solid #ffffff14;padding:16px 28px;display:flex;gap:24px;align-items:center}header strong{color:#a8caff;white-space:nowrap}input{background:#10141c;color:inherit;border:1px solid #46516d;border-radius:7px;padding:10px;width:min(380px,50vw)}.layout{display:flex;max-width:1600px;margin:auto}nav{width:250px;flex:none;position:sticky;top:76px;max-height:calc(100vh - 76px);overflow:auto;padding:24px 20px}nav a{display:block;font-size:13px;padding:5px 10px}a{color:#90baff;text-decoration:none}a:hover{text-decoration:underline}main{min-width:0;flex:1;padding:28px 36px}section{padding:18px 0 36px;border-bottom:1px solid #303b52;scroll-margin-top:20px}.filename{font:11px ui-monospace,monospace;letter-spacing:.1em;color:#788ca7}h2,h3,h4{color:#edf2fa;line-height:1.4}h2{font-size:27px}h3{font-size:20px;margin-top:32px}p{max-width:100ch;overflow-wrap:anywhere}pre{background:#171e2a;border:1px solid #303b52;border-radius:8px;padding:18px;white-space:pre-wrap;overflow-wrap:anywhere;font:13px/1.8 ui-monospace,monospace}code{color:#b0cdf7;font-size:.9em}pre code{font-size:inherit}li{margin:8px 0;overflow-wrap:anywhere}table{border-collapse:collapse;table-layout:fixed;width:100%;font-size:13px}td,th{border:1px solid #303b52;padding:10px 12px;text-align:left;vertical-align:top;overflow-wrap:anywhere}th{background:#1b2433;color:#edf2fa}td code{overflow-wrap:anywhere}@media(max-width:850px){nav{width:190px;padding:18px 6px}main{padding:20px}header{padding:12px 20px;gap:12px;font-size:13px}}@media(max-width:600px){nav{display:none}header{flex-wrap:wrap}input{width:100%}h2{font-size:23px}}
</style><header><strong>OMIX / Java Script SDK</strong><input type="search" aria-label="搜索文档" placeholder="搜索 API、生命周期或示例"></header><div class="layout"><nav>'''+''.join(navigation)+'''</nav><main>'''+''.join(sections)+'''</main></div><script>
const search=document.querySelector('input');search.oninput=()=>{const q=search.value.toLowerCase();document.querySelectorAll('section').forEach(s=>s.hidden=!s.textContent.toLowerCase().includes(q))};document.querySelectorAll('nav a').forEach(a=>a.onclick=()=>{search.value='';search.oninput()});
</script></html>'''
write(ROOT/'build/generated/script-docs/index.html',page)
print(f'Generated {len(entries)} SDK entries, {len(modes)} mode hosts, {len(names)} tools, {len(examples)} examples')
