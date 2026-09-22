<script lang="ts">
    import {onMount} from 'svelte';
    import {EditorView, keymap} from '@codemirror/view';
    import {EditorState} from '@codemirror/state';
    import {basicSetup} from 'codemirror';
    import {java} from '@codemirror/lang-java';
    import {autocompletion} from '@codemirror/autocomplete';
    import {setDiagnostics, type Diagnostic} from '@codemirror/lint';

    type Script = {id: string; loaded: boolean; changed: boolean; sourceHash: string; runningHash: string; generation: number};
    type Problem = {severity: string; message: string; line: number; column: number};
    type Job = {id: string; script: string; state: string; generation: number; error?: string; diagnostics: Problem[]; value?: unknown; phase?: string; startedAt?: number; finishedAt?: number};
    const phases: Record<string, string> = {queued: '等待编译', dependencies: '收集依赖', source: '解析源码', classpath: '准备类路径缓存（首次较慢）', java: '编译 Java', remap: '映射游戏名称', cache: '复用编译缓存', waiting_client: '等待客户端应用', applying: '应用脚本'};
    let scripts: Script[] = [], templates: string[] = [], selected = '', source = '', savedSource = '', hash = '';
    let directory = '', error = '', notice = '', busy = false, job: Job | null = null;
    let createId = '', template = 'Sprint', query = '', api: Array<{owner: string; signature: string}> = [];
    let logs: Array<{sequence: number; level: string; line: number; message: string}> = [], cursor = 0;
    let editorElement: HTMLDivElement, editor: EditorView | undefined;
    let showDocs = false, polling = false;
    let disposed = false, tab: 'logs' | 'api' | 'diagnostics' = 'logs';
    let completions: Array<{label: string; type: string; detail: string; owner: string}> = [];
    const apiOwners: Record<string, string> = Object.fromEntries(
        ['game', 'movement', 'inventory', 'modules', 'modes', 'commands', 'tools', 'events', 'tasks', 'packets', 'render', 'ui', 'storage', 'managers', 'nativeAccess']
            .map(name => [name, `cn.omix.script.api.ScriptApi.${name[0].toUpperCase()}${name.slice(1)}`]));
    apiOwners.script = 'cn.omix.script.api.ScriptContext';
    apiOwners.fisproxy = 'cn.omix.fisproxy.FisProxyManager';
    $: dirty = source !== savedSource;
    $: current = scripts.find(item => item.id === selected);

    async function call<T = any>(name: string, args: Record<string, unknown> = {}): Promise<T> {
        const response = await fetch('/api/v1/scripts/call', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({name, arguments: args})});
        const result = await response.json();
        if (!response.ok || result.error) throw new Error(result.error || `HTTP ${response.status}`);
        return result.value;
    }
    async function perform(action: () => Promise<void>) {
        if (busy) return; busy = true; error = ''; notice = '';
        try { await action(); } catch (failure) { error = failure instanceof Error ? failure.message : String(failure); }
        finally { busy = false; }
    }
    async function refresh() {
        const state = await call('script_status'); scripts = state.scripts; directory = state.directory;
    }
    function replaceSource(text: string) {
        editor?.dispatch({changes: {from: 0, to: editor.state.doc.length, insert: text}}); source = text;
    }
    async function select(id: string) {
        if (dirty && !window.confirm('当前编辑尚未保存。放弃这些修改？')) return;
        await perform(async () => {
            const value = scripts.find(item => item.id === id)?.sourceHash ? await call('script_read', {id}) : {hash: '', text: ''}; selected = id; hash = value.hash; savedSource = value.text;
            replaceSource(value.text); logs = []; cursor = 0; job = null;
            editor?.dispatch(setDiagnostics(editor.state, [])); await readLogs();
        });
    }
    async function save() {
        const value = await call('script_write', {id: selected, source, expectedHash: hash});
        hash = value.hash; savedSource = value.text; notice = '源码已保存；运行版本尚未改变。'; await refresh();
    }
    async function action(kind: string) {
        await perform(async () => {
            if (dirty && kind !== 'unload') await save();
            const result = await call('script_action', {id: selected, action: kind});
            if (result.id) { job = result; tab = 'diagnostics'; }
            else notice = '脚本已卸载。';
            await refresh();
        });
    }
    async function poll() {
        if (disposed || polling) return; polling = true;
        try {
            if (job && ['queued', 'compiling', 'applying'].includes(job.state)) {
                const jobId = job.id;
                const result = await call<Job>('script_job', {jobId});
                if (job?.id !== jobId || disposed) return;
                job = result;
                const diagnostics: Diagnostic[] = (job.diagnostics || []).filter(p => p.line > 0 && p.line <= (editor?.state.doc.lines || 0)).map(p => {
                    const line = editor!.state.doc.line(p.line);
                    return {from: line.from, to: line.to, severity: p.severity === 'ERROR' ? 'error' : 'warning', message: p.message};
                });
                if (editor) editor.dispatch(setDiagnostics(editor.state, diagnostics));
                if (!['queued', 'compiling', 'applying'].includes(job.state)) await refresh();
            }
            if (selected) await readLogs();
            await refresh();
        } catch (failure) { if (!disposed) error = String(failure); }
        finally { polling = false; }
    }
    async function readLogs() {
        const id = selected;
        const result = await call('script_logs', {id, after: cursor, limit: 100});
        if (selected !== id || disposed) return;
        logs = [...logs, ...result.entries].slice(-200); cursor = result.nextCursor;
    }
    function jump(line: number) {
        if (!editor || line < 1 || line > editor.state.doc.lines) return;
        const position = editor.state.doc.line(line).from;
        editor.dispatch({selection: {anchor: position}, effects: EditorView.scrollIntoView(position, {y: 'center'})}); editor.focus();
    }
    async function create() {
        await perform(async () => { await call('script_create', {id: createId, template}); await refresh(); notice = `已创建 ${createId}.java`; createId = ''; });
    }
    async function removeSource() {
        if (!window.confirm(`删除 ${selected}.java 的磁盘源码？运行版本会保持加载，直到手动卸载。`)) return;
        await perform(async () => {
            await call('script_delete', {id: selected, expectedHash: hash});
            savedSource = ''; hash = ''; replaceSource(''); notice = '源码已删除。运行版本可单独卸载。'; await refresh();
            if (!scripts.some(item => item.id === selected)) selected = '';
        });
    }
    async function cancelJob() {
        if (!job) return;
        await perform(async () => { await call('script_cancel', {jobId: job!.id}); job = await call('script_job', {jobId: job!.id}); });
    }
    async function openAi() {
        await fetch('/api/v1/scripts/openAi', {method: 'POST'});
    }
    onMount(() => {
        editor = new EditorView({parent: editorElement, state: EditorState.create({doc: '', extensions: [basicSetup, java(),
            autocompletion({override: [context => {
                const word = context.matchBefore(/[\w$]*/); if (!word) return null;
                const receiver = context.state.sliceDoc(Math.max(0, word.from - 80), word.from).match(/([\w$]+)\.$/)?.[1];
                if (word.from === word.to && !context.explicit && !receiver) return null;
                const owner = receiver ? apiOwners[receiver] : undefined;
                return {from: word.from, options: owner ? completions.filter(entry => entry.owner === owner) : completions, validFor: /^[\w$]*$/};
            }]}), keymap.of([{key: 'Mod-s', run: () => { if (selected) void perform(save); return true; }}]),
            EditorView.updateListener.of(update => { if (update.docChanged) source = update.state.doc.toString(); }),
            EditorView.theme({'&': {height: '100%', backgroundColor: '#11151b', color: '#dae3ee'}, '.cm-scroller': {overflow: 'auto'}, '.cm-gutters': {backgroundColor: '#171d25', color: '#718096', border: 'none'}, '.cm-content': {caretColor: '#80baff'}, '.cm-activeLine': {backgroundColor: '#ffffff08'}, '.cm-activeLineGutter': {backgroundColor: '#ffffff08'}}, {dark: true})
        ]})});
        void perform(async () => {
            await refresh(); templates = await call('script_templates');
            const entries = JSON.parse(await call<string>('script_reference', {path: 'api.json'}));
            completions = entries.filter((entry: any) => entry.owner.startsWith('cn.omix.script.api') || entry.owner === apiOwners.fisproxy || entry.kind === 'type')
                .map((entry: any) => ({label: entry.name, type: entry.kind === 'type' ? 'class' : entry.signature.includes('(') ? 'method' : 'property', detail: entry.signature, owner: entry.owner}));
        });
        void fetch('/api/v1/client/virtualScreen', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({name: 'scripts'})});
        const timer = window.setInterval(() => void poll(), 900);
        return () => { disposed = true; clearInterval(timer); editor?.destroy(); };
    });
</script>

{#if showDocs}<div class="docs"><button onclick={() => showDocs = false}>关闭文档 · 返回编辑器</button><iframe title="脚本文档" src="/script-docs/index.html"></iframe></div>{/if}
<div class="studio">
    <header><div><span class="brand">OMIX</span><strong>Script Studio</strong><span class="badge">JAVA 21</span></div><nav><a href="#/clickgui">ClickGUI</a><button onclick={() => showDocs = true}>脚本文档</button><button onclick={openAi}>打开 AI Agent</button></nav></header>
    <div class="workspace">
        <aside><h2>脚本 <span>{scripts.length}</span></h2><div class="script-list">
            {#each scripts as item}<button class:chosen={selected === item.id} onclick={() => select(item.id)} disabled={busy}><span class:live={item.loaded} class="dot"></span><span>{item.id}<small>.java</small></span>{#if item.changed}<span class="changed">待应用</span>{/if}</button>{/each}
            {#if !scripts.length}<p class="muted">从模板创建第一个脚本。<br />保存后，手动加载使其生效。</p>{/if}
        </div><div class="create"><label for="script-name">新建脚本</label><input id="script-name" bind:value={createId} placeholder="MyScript" /><select aria-label="模板" bind:value={template}>{#each templates as name}<option>{name}</option>{/each}</select><button class="primary" onclick={create} disabled={busy || !createId}>从模板创建</button></div><p class="directory" title={directory}>{directory}</p></aside>
        <main><div class="toolbar"><div><strong>{selected ? `${selected}.java` : '选择或创建脚本'}</strong>{#if dirty}<span class="changed">未保存</span>{/if}</div><div><button disabled={!selected || busy} onclick={() => perform(save)}>保存 ⌘/Ctrl S</button><button disabled={!selected || busy || !hash} onclick={removeSource}>删除源码</button><button disabled={!selected || busy} onclick={() => action('check')}>编译检查</button><button class="primary" disabled={!selected || busy} onclick={() => action(current?.loaded ? 'reload' : 'load')}>{current?.loaded ? '重载' : '加载'}</button><button disabled={!current?.loaded || busy} onclick={() => action('unload')}>卸载</button></div></div>
            <div class="version"><span>{current?.loaded ? `运行代次 ${current.generation} · ${current.runningHash.slice(0, 12)}` : '尚未加载'}</span><span>{hash ? `磁盘 ${hash.slice(0, 12)}` : ''}</span><span>保存不会自动重载</span></div>
            {#if error}<div class="message error" role="alert">{error}</div>{/if}{#if notice}<div class="message">{notice}</div>{/if}
            <div class="editor" bind:this={editorElement}></div>
            <section class="console"><div class="tabs"><button class:active={tab === 'logs'} onclick={() => tab = 'logs'}>运行日志</button><button class:active={tab === 'diagnostics'} onclick={() => tab = 'diagnostics'}>编译诊断 {job ? `· ${job.state}` : ''}</button><button class:active={tab === 'api'} onclick={() => tab = 'api'}>API 查询</button>{#if job && ['queued', 'compiling'].includes(job.state)}<button onclick={cancelJob} disabled={busy}>取消任务</button>{/if}</div><div class="output">
                {#if tab === 'logs'}{#each logs as entry}<button class="log" class:error={entry.level === 'ERROR'} onclick={() => jump(entry.line)}><small>{entry.level}{entry.line ? ` L${entry.line}` : ''}</small> {entry.message}</button>{/each}{#if !logs.length}<p class="muted">日志和运行错误将显示在这里。</p>{/if}
                {:else if tab === 'diagnostics'}{#if job}<p>任务 {job.id} · {job.state}{job.phase ? ` · ${phases[job.phase] || job.phase}` : ''}{job.startedAt ? ` · ${(((job.finishedAt || Date.now()) - job.startedAt) / 1000).toFixed(1)} 秒` : ''}</p>{#if job.error}<p class="error">{job.error}</p>{/if}{#each job.diagnostics || [] as problem}<button class="log" onclick={() => jump(problem.line)}>{problem.severity} · {problem.line}:{problem.column} — {problem.message}</button>{/each}{#if job.state === 'checked'}<p>编译检查通过，尚未加载此版本。</p>{:else if job.state === 'loaded'}<p>已应用运行代次 {job.generation}。</p>{/if}{:else}<p class="muted">点击“编译检查”查看源码诊断。</p>{/if}
                {:else}<form onsubmit={(event) => { event.preventDefault(); void perform(async () => { api = await call('script_api', {query, limit: 50}); }); }}><input aria-label="API 搜索" bind:value={query} placeholder="搜索接口、事件或 util，例如 RotationRequest" /><button type="submit">查询</button></form>{#each api as entry}<article><small>{entry.owner}</small><pre>{entry.signature}</pre></article>{/each}{/if}
            </div></section>
        </main>
    </div>
</div>
<style>
    .docs{position:fixed;inset:12px;z-index:10;background:#151b23;display:flex;flex-direction:column;border:1px solid #46516d;border-radius:8px;padding:10px;gap:10px}.docs iframe{flex:1;border:0;width:100%}.docs button{align-self:flex-end}
    .studio{height:100vh;display:flex;flex-direction:column;background:#11151b;color:#dbe3ed;font:13px -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;overflow:hidden}*{box-sizing:border-box}header{height:62px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 24px;border-bottom:1px solid #ffffff12;background:#151b23}header>div,nav,.toolbar,.toolbar>div{display:flex;align-items:center;gap:12px}.brand{letter-spacing:.2em;font-size:15px;color:#7ab3fb}.badge{font-size:10px;color:#7d90a8;border:1px solid #ffffff1a;padding:3px 5px;border-radius:4px}.workspace{display:flex;flex:1;min-height:0}aside{width:235px;flex:none;background:#151b23;border-right:1px solid #ffffff12;display:flex;flex-direction:column;padding:18px 12px;gap:12px}h2{font-size:13px;font-weight:500;margin:0 8px;color:#97a7bb}h2 span{float:right;color:#5f738e}.script-list{flex:1;overflow:auto}.script-list button{display:flex;gap:8px;width:100%;border-color:transparent;text-align:left;padding:10px 8px}.script-list button.chosen{background:#72afff1a;color:#a9ceff}.dot{display:inline-block;width:6px;height:6px;background:#526174;border-radius:50%;margin-top:5px}.dot.live{background:#70d3a5}small{color:#7c8ea5}.changed{font-size:10px;color:#e4b86a;margin-left:auto}.create{display:grid;gap:8px;padding:8px}.directory{font-size:10px;color:#58687c;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin:0 8px}main{flex:1;min-width:0;min-height:0;display:flex;flex-direction:column}.toolbar{padding:12px 16px;justify-content:space-between;flex-wrap:wrap;gap:8px}.version{padding:0 16px 10px;display:flex;gap:16px;color:#71849d;font:11px monospace}.editor{flex:1;min-height:120px;overflow:hidden;border-top:1px solid #ffffff0e;border-bottom:1px solid #ffffff14}.console{height:30%;min-height:140px;display:flex;flex-direction:column;background:#121820}.tabs{display:flex;gap:8px;padding:6px 12px;border-bottom:1px solid #ffffff0e}.tabs button{border:0}.tabs button.active{color:#8cbdff;background:#ffffff08}.output{overflow:auto;padding:8px 16px;flex:1}button,input,select{font:inherit;color:inherit;background:transparent;border:1px solid #ffffff20;border-radius:5px;padding:7px 10px}button,a{cursor:pointer}button:hover:not(:disabled){background:#ffffff0b}button:disabled{opacity:.4;cursor:default}.primary{background:#2961a9;border-color:#4c8fe6;color:#fff}.primary:hover:not(:disabled){background:#3273c6}input,select{background:#10151d;min-width:0}.muted{color:#71849d;line-height:1.8}.message{padding:8px 16px;background:#70d3a50a;color:#8fcdb3}.error{color:#ee9393!important}.log{display:block;text-align:left;font:12px/1.7 monospace;border:0;padding:3px 0;white-space:pre-wrap;word-break:break-word;width:100%}.log small{font-size:10px}a{color:#97b7df;text-decoration:none;font-size:12px}form{display:flex;gap:8px}form input{flex:1}article{padding:10px 0;border-bottom:1px solid #ffffff0b}pre{white-space:pre-wrap;margin:5px 0;font-size:12px}@media(max-width:850px){aside{width:175px}header{padding:0 12px}nav{gap:8px}.badge{display:none}.version{font-size:10px;gap:8px}.toolbar button{padding:6px}header strong{font-size:12px}}
</style>
