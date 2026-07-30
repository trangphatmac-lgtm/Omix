<script lang="ts">
    import {onMount} from "svelte";
    import omixLogo from "./assets/omix.png";

    type Theme = "light" | "dark";
    type View = "modules" | "configs";
    type ConnectionState = "connecting" | "ready" | "offline";

    interface Category {
        id: string;
        name: string;
    }

    interface MultiOption {
        name: string;
        value: boolean;
    }

    interface Setting {
        name: string;
        type: "boolean" | "number" | "mode" | "multi" | "color" | "text" | "key" | "unsupported";
        visible: boolean;
        value?: boolean | number | string;
        min?: number;
        max?: number;
        step?: number;
        keyName?: string;
        options?: Array<string | MultiOption>;
    }

    interface Module {
        name: string;
        category: string;
        enabled: boolean;
        hidden: boolean;
        key: number;
        keyName: string;
        settings: Setting[];
    }

    interface ClickGuiState {
        name: string;
        version: string;
        fps: number;
        ping: number;
        categories: Category[];
        modules: Module[];
        configs: string[];
        currentConfig: string;
    }

    interface BindingTarget {
        module: string;
        setting?: string;
    }

    const THEME_STORAGE_KEY = "clickgui.theme.v1";

    let state: ClickGuiState = emptyState();
    let theme: Theme = "light";
    let view: View = "modules";
    let activeCategory = "Combat";
    let selectedModuleName = "";
    let search = "";
    let mounted = false;
    let closing = false;
    let loading = true;
    let errorMessage = "";
    let toastMessage = "";
    let connectionState: ConnectionState = "connecting";
    let bindingTarget: BindingTarget | null = null;
    let openModeSetting = "";
    let socket: WebSocket | null = null;
    let toastTimer: number | undefined;
    let configName = "";
    let selectedConfig = "";
    let pendingAction = "";

    $: activeCategoryName = state.categories.find(category =>
        category.id === activeCategory)?.name ?? activeCategory;
    $: searchQuery = search.trim().toLowerCase();
    $: categoryModules = state.modules.filter(module =>
        searchQuery
            ? module.name.toLowerCase().includes(searchQuery)
            : module.category === activeCategory
    );
    $: selectedModule = state.modules.find(module =>
        module.name === selectedModuleName
        && module.category === activeCategory
    ) ?? state.modules.find(module => module.category === activeCategory) ?? null;
    $: visibleSettings = selectedModule?.settings.filter(setting =>
        setting.visible && setting.type !== "unsupported") ?? [];

    onMount(() => {
        const firstFrame = requestAnimationFrame(() => {
            requestAnimationFrame(() => mounted = true);
        });
        const onKeyDown = (event: KeyboardEvent) => {
            if (bindingTarget) {
                event.preventDefault();
                event.stopPropagation();
                void finishBinding(event);
                return;
            }
            if (event.key === "Escape") {
                if (openModeSetting) {
                    event.preventDefault();
                    openModeSetting = "";
                    return;
                }
                event.preventDefault();
                beginClose();
            }
        };
        const onPointerDown = (event: PointerEvent) => {
            const target = event.target;
            if (openModeSetting
                && target instanceof Element
                && !target.closest(".mode-select")) {
                openModeSetting = "";
            }
        };

        window.addEventListener("keydown", onKeyDown, true);
        window.addEventListener("pointerdown", onPointerDown);
        connectSocket();
        void acknowledgeScreen();
        void loadTheme();
        void loadState();

        return () => {
            cancelAnimationFrame(firstFrame);
            window.removeEventListener("keydown", onKeyDown, true);
            window.removeEventListener("pointerdown", onPointerDown);
            socket?.close();
            if (toastTimer) window.clearTimeout(toastTimer);
        };
    });

    function emptyState(): ClickGuiState {
        return {
            name: "Omix",
            version: "",
            fps: 0,
            ping: -1,
            categories: [],
            modules: [],
            configs: [],
            currentConfig: ""
        };
    }

    function connectSocket() {
        if (import.meta.env.DEV) {
            connectionState = "ready";
            return;
        }
        const scheme = location.protocol === "https:" ? "wss" : "ws";
        socket = new WebSocket(`${scheme}://${location.host}/ws`);
        socket.addEventListener("open", () => connectionState = "ready");
        socket.addEventListener("close", () => connectionState = "offline");
        socket.addEventListener("error", () => connectionState = "offline");
        socket.addEventListener("message", event => {
            try {
                const packet = JSON.parse(String(event.data)) as {
                    name?: string;
                    event?: {route?: string};
                };
                if (packet.name === "screenClosing" && packet.event?.route === "clickgui") {
                    beginClose();
                }
            } catch {
                // Ignore unrelated or malformed socket packets.
            }
        });
    }

    async function acknowledgeScreen() {
        if (import.meta.env.DEV) return;
        for (let attempt = 0; attempt < 40; attempt++) {
            try {
                const response = await post("/api/v1/client/virtualScreen", {name: "clickgui"});
                if (response.ok) return;
            } catch {
                // CEF can paint before the local bridge is ready.
            }
            await new Promise(resolve => setTimeout(resolve, 250));
        }
    }

    async function loadState(silent = false) {
        try {
            const response = await fetch("/api/v1/clickgui/state");
            state = await readState(response);
            loading = false;
            errorMessage = "";
            ensureSelection();
        } catch (error) {
            if (import.meta.env.DEV) {
                state = demoState();
                loading = false;
                connectionState = "ready";
                ensureSelection();
                return;
            }
            if (!silent) {
                loading = false;
                errorMessage = error instanceof Error ? error.message : String(error);
            }
        }
    }

    async function readState(response: Response): Promise<ClickGuiState> {
        if (!response.ok) {
            throw new Error((await response.text()) || `Request failed (${response.status})`);
        }
        if (!response.headers.get("content-type")?.includes("application/json")) {
            throw new Error("ClickGUI bridge returned an invalid response.");
        }
        return await response.json() as ClickGuiState;
    }

    function ensureSelection() {
        if (!state.categories.some(category => category.id === activeCategory)) {
            activeCategory = state.categories[0]?.id ?? "Combat";
        }
        const selectedStillExists = state.modules.some(module =>
            module.category === activeCategory && module.name === selectedModuleName);
        if (!selectedStillExists) {
            const candidates = state.modules.filter(module => module.category === activeCategory);
            selectedModuleName = candidates.find(module => module.enabled)?.name
                ?? candidates[0]?.name
                ?? "";
        }
        if (!selectedConfig || !state.configs.includes(selectedConfig)) {
            selectedConfig = state.currentConfig || state.configs[0] || "";
        }
    }

    function selectCategory(category: Category) {
        activeCategory = category.id;
        selectedModuleName = "";
        search = "";
        openModeSetting = "";
        view = "modules";
        ensureSelection();
    }

    function selectModule(module: Module) {
        activeCategory = module.category;
        selectedModuleName = module.name;
        openModeSetting = "";
    }

    async function setModuleEnabled(module: Module, enabled: boolean) {
        await mutate(
            `module:${module.name}`,
            "/api/v1/clickgui/module",
            {module: module.name, enabled}
        );
    }

    async function setSetting(setting: Setting, value: unknown, child?: string) {
        if (!selectedModule) return;
        await mutate(
            `setting:${selectedModule.name}:${setting.name}`,
            "/api/v1/clickgui/value",
            {
                module: selectedModule.name,
                setting: setting.name,
                value,
                ...(child ? {child} : {})
            }
        );
    }

    function toggleModeMenu(setting: Setting) {
        const key = modeSettingKey(setting);
        openModeSetting = openModeSetting === key ? "" : key;
    }

    async function chooseMode(setting: Setting, option: string) {
        openModeSetting = "";
        if (option !== String(setting.value ?? "")) {
            await setSetting(setting, option);
        }
    }

    async function mutate(action: string, path: string, body: unknown) {
        if (pendingAction) return;
        pendingAction = action;
        try {
            const response = await fetch(path, {
                method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(body)
            });
            state = await readState(response);
            ensureSelection();
        } catch (error) {
            if (import.meta.env.DEV) {
                applyDemoMutation(body);
            } else {
                showToast(error instanceof Error ? error.message : String(error), true);
            }
        } finally {
            pendingAction = "";
        }
    }

    function applyDemoMutation(body: unknown) {
        const packet = body as {
            module?: string;
            setting?: string;
            value?: unknown;
            child?: string;
            enabled?: boolean;
            key?: number;
        };
        state = {
            ...state,
            modules: state.modules.map(module => {
                if (module.name !== packet.module) return module;
                if (typeof packet.enabled === "boolean") {
                    return {...module, enabled: packet.enabled};
                }
                if (typeof packet.key === "number") {
                    return {...module, key: packet.key, keyName: keyLabel(packet.key)};
                }
                return {
                    ...module,
                    settings: module.settings.map(setting => {
                        if (setting.name !== packet.setting) return setting;
                        if (setting.type === "multi" && packet.child) {
                            return {
                                ...setting,
                                options: multiOptions(setting).map(option =>
                                    option.name === packet.child
                                        ? {...option, value: Boolean(packet.value)}
                                        : option
                                )
                            };
                        }
                        return {
                            ...setting,
                            value: packet.value as boolean | number | string,
                            ...(setting.type === "key" && typeof packet.value === "number"
                                ? {keyName: keyLabel(packet.value)}
                                : {})
                        };
                    })
                };
            })
        };
    }

    function startModuleBinding(module: Module) {
        bindingTarget = {module: module.name};
    }

    function startSettingBinding(module: Module, setting: Setting) {
        bindingTarget = {module: module.name, setting: setting.name};
    }

    async function finishBinding(event: KeyboardEvent) {
        const target = bindingTarget;
        bindingTarget = null;
        if (!target) return;
        const clearing = event.key === "Escape"
            || event.code === "Delete"
            || event.code === "Backspace";
        const key = clearing
            ? (target.setting ? 0 : -1)
            : glfwKeyFromCode(event.code);
        if (key === null) {
            showToast("这个按键暂不支持绑定。", true);
            return;
        }

        if (target.setting) {
            selectedModuleName = target.module;
            const module = state.modules.find(candidate => candidate.name === target.module);
            const setting = module?.settings.find(candidate => candidate.name === target.setting);
            if (setting) await setSetting(setting, key);
            return;
        }
        await mutate(
            `bind:${target.module}`,
            "/api/v1/clickgui/module",
            {module: target.module, key}
        );
    }

    async function runConfigAction(action: "create" | "load" | "save" | "delete") {
        const name = action === "create" ? configName.trim() : selectedConfig;
        if (!name || pendingAction) return;
        if (action === "delete" && name.toLowerCase() === "default") {
            showToast("Default 配置不能删除。", true);
            return;
        }
        if (action === "delete" && !window.confirm(`删除配置 “${name}”？`)) return;

        pendingAction = `config:${action}`;
        try {
            const response = await post("/api/v1/clickgui/config", {action, name});
            state = await readState(response);
            if (action === "create") {
                configName = "";
                selectedConfig = name;
                showToast(`已创建 ${name}`);
            } else {
                showToast(configActionLabel(action, name));
            }
            ensureSelection();
        } catch (error) {
            if (import.meta.env.DEV) {
                applyDemoConfigAction(action, name);
            } else {
                showToast(error instanceof Error ? error.message : String(error), true);
            }
        } finally {
            pendingAction = "";
        }
    }

    function applyDemoConfigAction(action: "create" | "load" | "save" | "delete", name: string) {
        if (action === "create" && !state.configs.includes(name)) {
            state = {...state, configs: [...state.configs, name]};
            selectedConfig = name;
            configName = "";
        } else if (action === "load") {
            state = {...state, currentConfig: name};
        } else if (action === "delete") {
            state = {...state, configs: state.configs.filter(config => config !== name)};
            selectedConfig = state.currentConfig;
        }
        showToast(configActionLabel(action, name));
    }

    function configActionLabel(action: string, name: string) {
        const verbs: Record<string, string> = {
            create: "已创建",
            load: "已加载",
            save: "已保存",
            delete: "已删除"
        };
        return `${verbs[action] ?? "已更新"} ${name}`;
    }

    function showToast(message: string, error = false) {
        toastMessage = `${error ? "!" : "✓"} ${message}`;
        if (toastTimer) window.clearTimeout(toastTimer);
        toastTimer = window.setTimeout(() => toastMessage = "", 2600);
    }

    function beginClose() {
        if (!closing) {
            closing = true;
            mounted = false;
        }
    }

    async function requestClose() {
        beginClose();
        if (!import.meta.env.DEV) {
            await post("/api/v1/client/closeScreen").catch(() => undefined);
        }
    }

    async function toggleTheme() {
        theme = theme === "light" ? "dark" : "light";
        try {
            await fetch("/api/v1/client/localStorage", {
                method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({key: THEME_STORAGE_KEY, value: theme})
            });
        } catch {
            localStorage.setItem(THEME_STORAGE_KEY, theme);
        }
    }

    async function loadTheme() {
        try {
            const response = await fetch(
                `/api/v1/client/localStorage?key=${encodeURIComponent(THEME_STORAGE_KEY)}`
            );
            if (!response.ok) throw new Error("No saved theme");
            const body = await response.json() as {value?: Theme};
            if (body.value === "dark" || body.value === "light") {
                theme = body.value;
                return;
            }
        } catch {
            const stored = localStorage.getItem(THEME_STORAGE_KEY);
            if (stored === "dark" || stored === "light") theme = stored;
        }
    }

    function post(path: string, body: unknown = {}) {
        return fetch(path, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(body)
        });
    }

    function modeOptions(setting: Setting): string[] {
        return (setting.options ?? []).filter((option): option is string =>
            typeof option === "string");
    }

    function modeSettingKey(setting: Setting) {
        return `${selectedModule?.name ?? ""}:${setting.name}`;
    }

    function categoryLabel(categoryId: string) {
        return state.categories.find(category => category.id === categoryId)?.name ?? categoryId;
    }

    function multiOptions(setting: Setting): MultiOption[] {
        return (setting.options ?? []).filter((option): option is MultiOption =>
            typeof option !== "string");
    }

    function formatNumber(value: unknown, step = 1) {
        const numeric = Number(value ?? 0);
        const decimals = String(step).includes(".")
            ? Math.min(3, String(step).split(".")[1]?.length ?? 0)
            : 0;
        return numeric.toFixed(decimals);
    }

    function keyLabel(key: number) {
        if (key <= 0) return "NONE";
        if (key >= 65 && key <= 90) return String.fromCharCode(key);
        if (key >= 48 && key <= 57) return String.fromCharCode(key);
        return `KEY ${key}`;
    }

    function glfwKeyFromCode(code: string): number | null {
        if (/^Key[A-Z]$/.test(code)) return code.charCodeAt(3);
        if (/^Digit[0-9]$/.test(code)) return code.charCodeAt(5);
        if (/^F([1-9]|1[0-9]|2[0-5])$/.test(code)) return 289 + Number(code.slice(1));
        if (/^Numpad[0-9]$/.test(code)) return 320 + Number(code.slice(6));

        const keys: Record<string, number> = {
            Space: 32,
            Quote: 39,
            Comma: 44,
            Minus: 45,
            Period: 46,
            Slash: 47,
            Semicolon: 59,
            Equal: 61,
            BracketLeft: 91,
            Backslash: 92,
            BracketRight: 93,
            Backquote: 96,
            Enter: 257,
            Tab: 258,
            Insert: 260,
            ArrowRight: 262,
            ArrowLeft: 263,
            ArrowDown: 264,
            ArrowUp: 265,
            PageUp: 266,
            PageDown: 267,
            Home: 268,
            End: 269,
            CapsLock: 280,
            ScrollLock: 281,
            NumLock: 282,
            PrintScreen: 283,
            Pause: 284,
            NumpadDecimal: 330,
            NumpadDivide: 331,
            NumpadMultiply: 332,
            NumpadSubtract: 333,
            NumpadAdd: 334,
            NumpadEnter: 335,
            NumpadEqual: 336,
            ShiftLeft: 340,
            ControlLeft: 341,
            AltLeft: 342,
            MetaLeft: 343,
            ShiftRight: 344,
            ControlRight: 345,
            AltRight: 346,
            MetaRight: 347,
            ContextMenu: 348
        };
        return keys[code] ?? null;
    }

    function demoState(): ClickGuiState {
        return {
            name: "Omix",
            version: "260726-SNAPSHOT",
            fps: 97,
            ping: 14,
            currentConfig: "Default",
            configs: ["Default", "Hypixel", "Legit"],
            categories: [
                {id: "Combat", name: "Combat"},
                {id: "Exploits", name: "Exploit"},
                {id: "Move", name: "Move"},
                {id: "Player", name: "Player"},
                {id: "World", name: "World"},
                {id: "Render", name: "Render"}
            ],
            modules: [
                {
                    name: "Aura",
                    category: "Combat",
                    enabled: true,
                    hidden: false,
                    key: 82,
                    keyName: "R",
                    settings: [
                        {name: "Target Mode", type: "mode", visible: true, value: "Single", options: ["Single", "Switch"]},
                        {name: "Priority", type: "mode", visible: true, value: "Distance", options: ["Distance", "Health", "Fov", "Armor"]},
                        {name: "Max CPS", type: "number", visible: true, value: 14, min: 1, max: 20, step: 1},
                        {name: "Min CPS", type: "number", visible: true, value: 8, min: 1, max: 20, step: 1},
                        {name: "Range", type: "number", visible: true, value: 3.8, min: 3, max: 8, step: .1},
                        {name: "Ray Cast", type: "boolean", visible: true, value: true}
                    ]
                },
                {
                    name: "Criticals",
                    category: "Combat",
                    enabled: false,
                    hidden: false,
                    key: -1,
                    keyName: "None",
                    settings: [{name: "Mode", type: "mode", visible: true, value: "Packet", options: ["Packet", "NCP", "Strict"]}]
                },
                {
                    name: "Velocity",
                    category: "Combat",
                    enabled: true,
                    hidden: false,
                    key: -1,
                    keyName: "None",
                    settings: [
                        {name: "Mode", type: "mode", visible: true, value: "Packet", options: ["Normal", "Packet", "Reduce"]},
                        {name: "Horizontal", type: "number", visible: true, value: 0, min: 0, max: 100, step: 1},
                        {name: "Vertical", type: "number", visible: true, value: 0, min: 0, max: 100, step: 1}
                    ]
                },
                {name: "AutoTotem", category: "Combat", enabled: false, hidden: false, key: -1, keyName: "None", settings: []},
                {name: "FastBow", category: "Combat", enabled: false, hidden: false, key: -1, keyName: "None", settings: [{name: "Packets", type: "number", visible: true, value: 20, min: 1, max: 20, step: 1}]},
                {name: "FastEat", category: "Combat", enabled: true, hidden: false, key: -1, keyName: "None", settings: []},
                {name: "TargetStrafe", category: "Combat", enabled: false, hidden: false, key: -1, keyName: "None", settings: []},
                {name: "Disabler", category: "Exploits", enabled: false, hidden: false, key: -1, keyName: "None", settings: []},
                {name: "PathFinder", category: "Exploits", enabled: false, hidden: false, key: 80, keyName: "P", settings: []},
                {name: "Speed", category: "Move", enabled: true, hidden: false, key: 71, keyName: "G", settings: []},
                {name: "Fly", category: "Move", enabled: false, hidden: false, key: 70, keyName: "F", settings: []},
                {
                    name: "Targets",
                    category: "Player",
                    enabled: true,
                    hidden: true,
                    key: -1,
                    keyName: "None",
                    settings: [{
                        name: "Target",
                        type: "multi",
                        visible: true,
                        options: [
                            {name: "Player", value: true},
                            {name: "Invisible", value: false},
                            {name: "Mob", value: false}
                        ]
                    }]
                },
                {name: "Scaffold", category: "World", enabled: false, hidden: false, key: 86, keyName: "V", settings: []},
                {
                    name: "HUD",
                    category: "Render",
                    enabled: true,
                    hidden: false,
                    key: -1,
                    keyName: "None",
                    settings: [
                        {name: "Accent", type: "color", visible: true, value: "#6576ed"},
                        {name: "Watermark", type: "text", visible: true, value: "Omix"}
                    ]
                },
                {name: "ESP", category: "Render", enabled: true, hidden: false, key: -1, keyName: "None", settings: []},
                {name: "ClickGui", category: "Render", enabled: false, hidden: false, key: 344, keyName: "RIGHT_SHIFT", settings: []}
            ]
        };
    }
</script>

<svelte:head>
    <title>Omix ClickGUI</title>
</svelte:head>

<main
    class:mounted
    class:closing
    class:dark-theme={theme === "dark"}
    class="clickgui-screen"
    aria-label="Omix ClickGUI"
>
    <section class="window-shell">
        <aside class="sidebar" aria-label="Categories">
            <button class="brand-button" title="Omix" on:click={() => view = "modules"}>
                <img src={omixLogo} alt="" aria-hidden="true"/>
            </button>

            <nav class="category-nav">
                {#each state.categories as category}
                    <button
                        class:active={view === "modules" && activeCategory === category.id}
                        class="nav-button"
                        title={category.name}
                        aria-label={category.name}
                        on:click={() => selectCategory(category)}
                    >
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            {#if category.id === "Combat"}
                                <path d="m5 19 5.2-5.2m3.6-3.6L19 5m-9.5.5 9 9M7.2 4.2l2.6 1.3-4.3 4.3-1.3-2.6 3-3Zm9.6 15.6-2.6-1.3 4.3-4.3 1.3 2.6-3 3Z"/>
                            {:else if category.id === "Exploits"}
                                <path d="M8.5 8.5 5 12l3.5 3.5M15.5 8.5 19 12l-3.5 3.5M13.5 5l-3 14"/>
                            {:else if category.id === "Move"}
                                <circle cx="14.8" cy="5.5" r="1.8"/><path d="m12.8 9.1 2.4 2.1 3.1.5M12.8 9.1 10 12l-3.2 1m6-3.9-1.1 5.2-3.4 4.2m3.4-4.2 3 1.6 1 3.1"/>
                            {:else if category.id === "Player"}
                                <circle cx="12" cy="8" r="3"/><path d="M5.5 19c.7-4 2.8-6 6.5-6s5.8 2 6.5 6"/>
                            {:else if category.id === "World"}
                                <circle cx="12" cy="12" r="8"/><path d="M4.5 10h15M12 4c2.2 2.4 3.2 5.1 3.2 8S14.2 17.6 12 20c-2.2-2.4-3.2-5.1-3.2-8S9.8 6.4 12 4Z"/>
                            {:else}
                                <path d="M3.5 12s3.2-5 8.5-5 8.5 5 8.5 5-3.2 5-8.5 5-8.5-5-8.5-5Z"/><circle cx="12" cy="12" r="2.4"/>
                            {/if}
                        </svg>
                        <span class="tooltip">{category.name}</span>
                    </button>
                {/each}
            </nav>

            <div class="sidebar-footer">
                <button
                    class:active={view === "configs"}
                    class="nav-button"
                    title="Configs"
                    aria-label="Configs"
                    on:click={() => view = "configs"}
                >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M5 4h12l2 2v14H5V4Z"/><path d="M8 4v6h8V4M8 20v-6h8v6"/>
                    </svg>
                    <span class="tooltip">Configs</span>
                </button>
            </div>
        </aside>

        <div class="app-column">
            <header class="titlebar">
                <div class="breadcrumb">
                    <strong>{view === "configs" ? "Configs" : activeCategoryName}</strong>
                    <span class="status-dot" class:offline={connectionState === "offline"}></span>
                    <span>{state.version || "WebUI"}</span>
                    <span>—</span>
                    <span>{connectionState === "ready" ? "Connected" : connectionState}</span>
                </div>

                <div class="window-actions">
                    <button class="theme-button" title="切换深色/浅色模式" on:click={toggleTheme}>
                        {#if theme === "light"}
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                                <circle cx="12" cy="12" r="3.5"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4m10.6 10.6 1.4 1.4m0-13.4-1.4 1.4M6.7 17.3l-1.4 1.4"/>
                            </svg>
                        {:else}
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                                <path d="M19.5 15.1A8 8 0 0 1 8.9 4.5a8 8 0 1 0 10.6 10.6Z"/>
                            </svg>
                        {/if}
                    </button>
                    <button class="traffic red" aria-label="Close" title="关闭" on:click={requestClose}></button>
                    <button class="traffic amber" aria-label="Refresh" title="刷新" on:click={() => loadState()}></button>
                    <span class="traffic green" aria-hidden="true"></span>
                </div>
            </header>

            {#if loading}
                <div class="loading-state">
                    <span class="loader"></span>
                    <p>Loading module state…</p>
                </div>
            {:else if errorMessage}
                <div class="loading-state error">
                    <strong>ClickGUI bridge unavailable</strong>
                    <p>{errorMessage}</p>
                    <button on:click={() => loadState()}>Retry</button>
                </div>
            {:else if view === "configs"}
                <section class="config-workspace">
                    <article class="panel config-list-panel">
                        <div class="panel-heading">
                            <div>
                                <span class="eyebrow">Profiles</span>
                                <h2>Configurations</h2>
                            </div>
                            <span class="count-badge">{state.configs.length}</span>
                        </div>

                        <div class="config-list">
                            {#each state.configs as config}
                                <button
                                    class:active={selectedConfig === config}
                                    class="config-item"
                                    on:click={() => selectedConfig = config}
                                >
                                    <span class="config-icon">
                                        <svg viewBox="0 0 24 24" aria-hidden="true">
                                            <path d="M6 3.5h10l2 2V20H6V3.5Z"/><path d="M9 3.5v5h6v-5M9 20v-6h6v6"/>
                                        </svg>
                                    </span>
                                    <span>
                                        <strong>{config}</strong>
                                        <small>{state.currentConfig === config ? "Active profile" : "Saved profile"}</small>
                                    </span>
                                    {#if state.currentConfig === config}
                                        <span class="active-pill">ACTIVE</span>
                                    {/if}
                                </button>
                            {/each}
                        </div>
                    </article>

                    <article class="panel config-actions-panel">
                        <div class="config-hero">
                            <span class="config-hero-icon">
                                <svg viewBox="0 0 24 24" aria-hidden="true">
                                    <path d="M4.5 7.5h15v11h-15v-11Z"/><path d="M8 7.5V4h8v3.5M8 12h8M8 15.5h5"/>
                                </svg>
                            </span>
                            <div>
                                <span class="eyebrow">Selected config</span>
                                <h2>{selectedConfig || "No configuration"}</h2>
                                <p>保存当前模块、键位和全部设置，或恢复已有配置。</p>
                            </div>
                        </div>

                        <div class="config-create">
                            <label for="config-name">Create a new profile</label>
                            <div class="input-action">
                                <input
                                    id="config-name"
                                    maxlength="48"
                                    placeholder="Config name"
                                    bind:value={configName}
                                    on:keydown={(event) => {
                                        if (event.key === "Enter") void runConfigAction("create");
                                    }}
                                />
                                <button
                                    disabled={!configName.trim() || Boolean(pendingAction)}
                                    on:click={() => runConfigAction("create")}
                                >Create</button>
                            </div>
                        </div>

                        <div class="config-action-grid">
                            <button
                                disabled={!selectedConfig || Boolean(pendingAction)}
                                on:click={() => runConfigAction("load")}
                            >
                                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11m0 0-4-4m4 4 4-4M5 19h14"/></svg>
                                <span><strong>Load</strong><small>Apply selected profile</small></span>
                            </button>
                            <button
                                disabled={!selectedConfig || Boolean(pendingAction)}
                                on:click={() => runConfigAction("save")}
                            >
                                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h12l2 2v14H5V4Z"/><path d="M8 4v6h8V4M8 20v-6h8v6"/></svg>
                                <span><strong>Save</strong><small>Overwrite with current state</small></span>
                            </button>
                            <button
                                class="danger"
                                disabled={!selectedConfig || selectedConfig.toLowerCase() === "default" || Boolean(pendingAction)}
                                on:click={() => runConfigAction("delete")}
                            >
                                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 7h14M9 7V4h6v3m2 0-1 13H8L7 7m3.5 4v5m3-5v5"/></svg>
                                <span><strong>Delete</strong><small>Remove selected profile</small></span>
                            </button>
                        </div>
                    </article>
                </section>
            {:else}
                <section class="module-workspace">
                    <article class="panel settings-panel">
                        {#if selectedModule}
                            <div class="module-heading">
                                <div class="module-title">
                                    <span class="accent-line"></span>
                                    <div>
                                        <span class="eyebrow">{activeCategoryName} module</span>
                                        <h1>{selectedModule.name}</h1>
                                    </div>
                                </div>
                                <button
                                    class:enabled={selectedModule.enabled}
                                    class="power-button"
                                    aria-label={selectedModule.enabled ? "Disable module" : "Enable module"}
                                    disabled={Boolean(pendingAction)}
                                    on:click={() => setModuleEnabled(selectedModule!, !selectedModule!.enabled)}
                                >
                                    <svg viewBox="0 0 24 24" aria-hidden="true">
                                        <path d="M12 3v8M7.1 6.7a7 7 0 1 0 9.8 0"/>
                                    </svg>
                                </button>
                            </div>

                            <div class="module-meta">
                                <label class="enabled-check">
                                    <input
                                        type="checkbox"
                                        checked={selectedModule.enabled}
                                        disabled={Boolean(pendingAction)}
                                        on:change={(event) => setModuleEnabled(
                                            selectedModule!,
                                            (event.currentTarget as HTMLInputElement).checked
                                        )}
                                    />
                                    <span></span>
                                    <strong>Enabled</strong>
                                </label>
                                <button
                                    class:binding={bindingTarget?.module === selectedModule.name && !bindingTarget.setting}
                                    class="bind-button"
                                    on:click={() => startModuleBinding(selectedModule!)}
                                >
                                    <svg viewBox="0 0 24 24" aria-hidden="true">
                                        <rect x="3.5" y="6" width="17" height="12" rx="2"/><path d="M7 10h.01M10 10h.01M13 10h.01M16 10h.01M8 14h8"/>
                                    </svg>
                                    {bindingTarget?.module === selectedModule.name && !bindingTarget.setting
                                        ? "Press a key…"
                                        : selectedModule.keyName}
                                </button>
                            </div>

                            <div class="settings-scroll">
                                {#if visibleSettings.length === 0}
                                    <div class="empty-settings">
                                        <svg viewBox="0 0 24 24" aria-hidden="true">
                                            <path d="M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Z"/><path d="m19 13 .2-1-.2-1 2-1.5-2-3.4-2.4 1a8 8 0 0 0-1.7-1L14.5 3h-5l-.4 3.1a8 8 0 0 0-1.7 1L5 6.1 3 9.5 5 11l-.2 1 .2 1-2 1.5 2 3.4 2.4-1a8 8 0 0 0 1.7 1l.4 3.1h5l.4-3.1a8 8 0 0 0 1.7-1l2.4 1 2-3.4L19 13Z"/>
                                        </svg>
                                        <strong>No settings</strong>
                                        <span>这个模块没有可配置参数。</span>
                                    </div>
                                {:else}
                                    {#each visibleSettings as setting}
                                        {#if setting.type === "boolean"}
                                            <label class="setting-row boolean-row">
                                                <span class="setting-copy">
                                                    <strong>{setting.name}</strong>
                                                    <small>Toggle this option</small>
                                                </span>
                                                <input
                                                    class="native-toggle"
                                                    type="checkbox"
                                                    checked={Boolean(setting.value)}
                                                    disabled={Boolean(pendingAction)}
                                                    on:change={(event) => setSetting(
                                                        setting,
                                                        (event.currentTarget as HTMLInputElement).checked
                                                    )}
                                                />
                                                <span class="switch-track"></span>
                                            </label>
                                        {:else if setting.type === "number"}
                                            <div class="setting-row slider-row">
                                                <div class="setting-label-line">
                                                    <strong>{setting.name}</strong>
                                                    <output>{formatNumber(setting.value, setting.step)}</output>
                                                </div>
                                                <input
                                                    type="range"
                                                    min={setting.min}
                                                    max={setting.max}
                                                    step={setting.step}
                                                    value={Number(setting.value)}
                                                    disabled={Boolean(pendingAction)}
                                                    style={`--range-progress: ${(Number(setting.value) - Number(setting.min)) / Math.max(.0001, Number(setting.max) - Number(setting.min)) * 100}%`}
                                                    on:change={(event) => setSetting(
                                                        setting,
                                                        Number((event.currentTarget as HTMLInputElement).value)
                                                    )}
                                                />
                                                <div class="range-bounds">
                                                    <span>{formatNumber(setting.min, setting.step)}</span>
                                                    <span>{formatNumber(setting.max, setting.step)}</span>
                                                </div>
                                            </div>
                                        {:else if setting.type === "mode"}
                                            <div
                                                class:mode-open={openModeSetting === modeSettingKey(setting)}
                                                class="setting-row select-row"
                                            >
                                                <span class="setting-copy">
                                                    <strong>{setting.name}</strong>
                                                    <small>Select behavior</small>
                                                </span>
                                                <div
                                                    class:open={openModeSetting === modeSettingKey(setting)}
                                                    class="mode-select"
                                                >
                                                    <button
                                                        class="mode-trigger"
                                                        type="button"
                                                        disabled={Boolean(pendingAction)}
                                                        aria-haspopup="listbox"
                                                        aria-expanded={openModeSetting === modeSettingKey(setting)}
                                                        on:click={() => toggleModeMenu(setting)}
                                                    >
                                                        <span>{String(setting.value ?? "")}</span>
                                                        <svg viewBox="0 0 24 24" aria-hidden="true">
                                                            <path d="m8 10 4 4 4-4"/>
                                                        </svg>
                                                    </button>
                                                    {#if openModeSetting === modeSettingKey(setting)}
                                                        <div class="mode-menu" role="listbox" aria-label={setting.name}>
                                                            {#each modeOptions(setting) as option}
                                                                <button
                                                                    class:selected={option === String(setting.value ?? "")}
                                                                    class="mode-option"
                                                                    type="button"
                                                                    role="option"
                                                                    aria-selected={option === String(setting.value ?? "")}
                                                                    on:click={() => chooseMode(setting, option)}
                                                                >
                                                                    <span>{option}</span>
                                                                    {#if option === String(setting.value ?? "")}
                                                                        <svg viewBox="0 0 16 16" aria-hidden="true">
                                                                            <path d="M3.2 8.1 6.6 11.3 12.9 4.9"/>
                                                                        </svg>
                                                                    {/if}
                                                                </button>
                                                            {/each}
                                                        </div>
                                                    {/if}
                                                </div>
                                            </div>
                                        {:else if setting.type === "multi"}
                                            <div class="setting-row multi-row">
                                                <div class="setting-label-line">
                                                    <strong>{setting.name}</strong>
                                                    <small>Multiple selection</small>
                                                </div>
                                                <div class="multi-options">
                                                    {#each multiOptions(setting) as option}
                                                        <label>
                                                            <input
                                                                type="checkbox"
                                                                checked={option.value}
                                                                disabled={Boolean(pendingAction)}
                                                                on:change={(event) => setSetting(
                                                                    setting,
                                                                    (event.currentTarget as HTMLInputElement).checked,
                                                                    option.name
                                                                )}
                                                            />
                                                            <span></span>
                                                            {option.name}
                                                        </label>
                                                    {/each}
                                                </div>
                                            </div>
                                        {:else if setting.type === "color"}
                                            <label class="setting-row color-row">
                                                <span class="setting-copy">
                                                    <strong>{setting.name}</strong>
                                                    <small>{String(setting.value)}</small>
                                                </span>
                                                <span class="color-control">
                                                    <input
                                                        type="color"
                                                        value={String(setting.value)}
                                                        disabled={Boolean(pendingAction)}
                                                        on:change={(event) => setSetting(
                                                            setting,
                                                            (event.currentTarget as HTMLInputElement).value
                                                        )}
                                                    />
                                                </span>
                                            </label>
                                        {:else if setting.type === "text"}
                                            <label class="setting-row text-row">
                                                <span class="setting-copy">
                                                    <strong>{setting.name}</strong>
                                                    <small>Text value</small>
                                                </span>
                                                <input
                                                    type="text"
                                                    value={String(setting.value ?? "")}
                                                    disabled={Boolean(pendingAction)}
                                                    on:change={(event) => setSetting(
                                                        setting,
                                                        (event.currentTarget as HTMLInputElement).value
                                                    )}
                                                />
                                            </label>
                                        {:else if setting.type === "key"}
                                            <div class="setting-row key-row">
                                                <span class="setting-copy">
                                                    <strong>{setting.name}</strong>
                                                    <small>Keyboard shortcut</small>
                                                </span>
                                                <button
                                                    class:binding={bindingTarget?.module === selectedModule.name
                                                        && bindingTarget.setting === setting.name}
                                                    on:click={() => startSettingBinding(selectedModule!, setting)}
                                                >
                                                    {bindingTarget?.module === selectedModule.name
                                                        && bindingTarget.setting === setting.name
                                                        ? "Press a key…"
                                                        : setting.keyName}
                                                </button>
                                            </div>
                                        {/if}
                                    {/each}
                                {/if}
                            </div>
                        {/if}
                    </article>

                    <article class="panel modules-panel">
                        <div class="panel-heading">
                            <div>
                                <span class="eyebrow">Module browser</span>
                                <h2>{searchQuery ? "All modules" : activeCategoryName}</h2>
                            </div>
                            <span class="count-badge">{categoryModules.length}</span>
                        </div>

                        <label class="search-box">
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                                <circle cx="10.5" cy="10.5" r="5.5"/><path d="m15 15 4.5 4.5"/>
                            </svg>
                            <input placeholder="Search all modules" bind:value={search}/>
                            {#if search}
                                <button aria-label="Clear search" on:click={() => search = ""}>×</button>
                            {/if}
                        </label>

                        <div class="module-list">
                            {#each categoryModules as module}
                                <div
                                    class:active={selectedModule?.name === module.name}
                                    class:enabled={module.enabled}
                                    class="module-item"
                                    role="button"
                                    tabindex="0"
                                    on:click={() => selectModule(module)}
                                    on:keydown={(event) => {
                                        if (event.key === "Enter" || event.key === " ") {
                                            event.preventDefault();
                                            selectModule(module);
                                        }
                                    }}
                                >
                                    <button
                                        class:checked={module.enabled}
                                        class="module-checkbox"
                                        aria-pressed={module.enabled}
                                        aria-label={module.enabled ? `Disable ${module.name}` : `Enable ${module.name}`}
                                        on:click={(event) => {
                                            event.stopPropagation();
                                            void setModuleEnabled(module, !module.enabled);
                                        }}
                                    >
                                        {#if module.enabled}
                                            <svg viewBox="0 0 16 16" aria-hidden="true">
                                                <path d="M3.2 8.1 6.6 11.3 12.9 4.9"/>
                                            </svg>
                                        {/if}
                                    </button>
                                    <span class="module-item-copy">
                                        <strong>{module.name}</strong>
                                        <small>
                                            {#if searchQuery}
                                                {categoryLabel(module.category)} ·
                                            {/if}
                                            {module.settings.filter(setting => setting.visible).length} settings
                                        </small>
                                    </span>
                                    <span class="module-gear">
                                        <svg viewBox="0 0 24 24" aria-hidden="true">
                                            <circle cx="12" cy="12" r="2.8"/><path d="m19 13 .2-1-.2-1 1.7-1.3-1.8-3.1-2.1.8a8 8 0 0 0-1.5-.9L15 3.8h-6l-.3 2.7a8 8 0 0 0-1.5.9l-2.1-.8-1.8 3.1L5 11l-.2 1 .2 1-1.7 1.3 1.8 3.1 2.1-.8a8 8 0 0 0 1.5.9l.3 2.7h6l.3-2.7a8 8 0 0 0 1.5-.9l2.1.8 1.8-3.1L19 13Z"/>
                                        </svg>
                                    </span>
                                </div>
                            {:else}
                                <div class="no-results">No modules match “{search}”.</div>
                            {/each}
                        </div>
                    </article>
                </section>
            {/if}

            <footer class="statusbar">
                <div>
                    <span>STATUS: <strong>ACTIVE</strong></span>
                    <span>FPS: {state.fps || "—"}</span>
                    <span>PING: {state.ping >= 0 ? `${state.ping}ms` : "—"}</span>
                </div>
                <span class="protected"><i></i> CONFIG SYNCED</span>
            </footer>
        </div>
    </section>

    {#if toastMessage}
        <div class:error={toastMessage.startsWith("!")} class="toast">{toastMessage}</div>
    {/if}
</main>

<style>
    .clickgui-screen {
        --accent: #6874ec;
        --accent-rgb: 104, 116, 236;
        --shell: rgba(225, 227, 234, .88);
        --sidebar: rgba(244, 245, 249, .76);
        --surface: rgba(250, 250, 252, .85);
        --surface-solid: #f7f7fa;
        --surface-hover: rgba(255, 255, 255, .96);
        --soft: rgba(235, 236, 242, .82);
        --line: rgba(87, 92, 111, .13);
        --line-strong: rgba(75, 80, 99, .2);
        --text: #20232c;
        --muted: #818592;
        --faint: #aaadb7;
        --shadow: rgba(23, 27, 41, .28);
        --screen-pad-x: clamp(22px, 4vw, 72px);
        --screen-pad-y: clamp(22px, 5vh, 58px);
        position: relative;
        display: grid;
        width: 100%;
        height: 100%;
        padding: var(--screen-pad-y) var(--screen-pad-x);
        place-items: center;
        color: var(--text);
        opacity: 0;
        transform: translateY(28px) scale(.96);
        filter: blur(6px);
        transition:
            opacity 240ms cubic-bezier(.22, 1, .36, 1),
            transform 430ms cubic-bezier(.16, 1, .3, 1),
            filter 300ms ease;
    }

    .clickgui-screen.dark-theme {
        --shell: rgba(28, 31, 40, .92);
        --sidebar: rgba(23, 26, 34, .8);
        --surface: rgba(38, 41, 52, .9);
        --surface-solid: #272a34;
        --surface-hover: rgba(49, 53, 66, .98);
        --soft: rgba(29, 32, 41, .84);
        --line: rgba(255, 255, 255, .09);
        --line-strong: rgba(255, 255, 255, .16);
        --text: #f1f2f7;
        --muted: #9b9fac;
        --faint: #6f7482;
        --shadow: rgba(0, 0, 0, .5);
    }

    .clickgui-screen.mounted {
        opacity: 1;
        transform: translateY(0) scale(1);
        filter: blur(0);
    }

    .clickgui-screen.closing {
        pointer-events: none;
        opacity: 0;
        transform: translateY(20px) scale(.975);
        filter: blur(6px);
        transition-duration: 260ms;
    }

    button,
    input {
        color: inherit;
    }

    button {
        border: 0;
    }

    svg {
        display: block;
        fill: none;
        stroke: currentColor;
        stroke-width: 1.7;
        stroke-linecap: round;
        stroke-linejoin: round;
    }

    .window-shell {
        display: grid;
        width: min(1160px, calc(100vw - var(--screen-pad-x) - var(--screen-pad-x)));
        height: min(735px, calc(100vh - var(--screen-pad-y) - var(--screen-pad-y)));
        min-height: min(480px, calc(100vh - var(--screen-pad-y) - var(--screen-pad-y)));
        grid-template-columns: 74px minmax(0, 1fr);
        overflow: hidden;
        border: 1px solid rgba(255, 255, 255, .52);
        border-radius: 28px;
        background: var(--shell);
        box-shadow:
            inset 0 1px 0 rgba(255, 255, 255, .7),
            0 30px 100px var(--shadow),
            0 8px 24px rgba(23, 27, 41, .16);
        backdrop-filter: blur(34px) saturate(132%);
        -webkit-backdrop-filter: blur(34px) saturate(132%);
    }

    .sidebar {
        display: flex;
        min-height: 0;
        padding: 20px 11px 16px;
        align-items: center;
        flex-direction: column;
        border-right: 1px solid var(--line);
        background: var(--sidebar);
    }

    .brand-button,
    .nav-button {
        position: relative;
        display: grid;
        flex: 0 0 auto;
        place-items: center;
        color: var(--muted);
        cursor: pointer;
        background: transparent;
        transition:
            color 180ms ease,
            transform 180ms cubic-bezier(.2, .8, .2, 1),
            background 180ms ease,
            box-shadow 180ms ease;
    }

    .brand-button {
        width: 48px;
        height: 48px;
        margin-bottom: 19px;
        border: 1px solid rgba(var(--accent-rgb), .28);
        border-radius: 15px;
        color: var(--accent);
        background: rgba(var(--accent-rgb), .11);
        box-shadow:
            inset 0 1px rgba(255, 255, 255, .72),
            0 8px 20px rgba(var(--accent-rgb), .15);
    }

    .brand-button img {
        display: block;
        width: 32px;
        height: 32px;
        object-fit: contain;
    }

    .category-nav {
        display: flex;
        min-height: 0;
        align-items: center;
        flex-direction: column;
        gap: 7px;
    }

    .nav-button {
        width: 43px;
        height: 43px;
        border-radius: 13px;
    }

    .nav-button svg {
        width: 21px;
        height: 21px;
    }

    .nav-button:hover {
        color: var(--text);
        background: rgba(var(--accent-rgb), .08);
        transform: translateY(-1px);
    }

    .nav-button.active {
        color: var(--accent);
        background: rgba(var(--accent-rgb), .13);
        box-shadow: inset 3px 0 var(--accent);
    }

    .tooltip {
        position: absolute;
        z-index: 12;
        left: calc(100% + 12px);
        padding: 6px 9px;
        border: 1px solid var(--line);
        border-radius: 8px;
        color: var(--text);
        font-size: 11px;
        white-space: nowrap;
        pointer-events: none;
        opacity: 0;
        transform: translateX(-4px);
        background: var(--surface-solid);
        box-shadow: 0 8px 22px var(--shadow);
        transition: opacity 150ms ease, transform 150ms ease;
    }

    .nav-button:hover .tooltip {
        opacity: 1;
        transform: translateX(0);
    }

    .sidebar-footer {
        margin-top: auto;
        padding-top: 12px;
    }

    .app-column {
        display: grid;
        min-width: 0;
        min-height: 0;
        grid-template-rows: 58px minmax(0, 1fr) 27px;
    }

    .titlebar {
        display: flex;
        min-width: 0;
        padding: 0 21px;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid var(--line);
        background: rgba(255, 255, 255, .24);
    }

    .dark-theme .titlebar {
        background: rgba(255, 255, 255, .025);
    }

    .breadcrumb,
    .window-actions {
        display: flex;
        align-items: center;
    }

    .breadcrumb {
        min-width: 0;
        gap: 8px;
        color: var(--muted);
        font-size: 12px;
        white-space: nowrap;
    }

    .breadcrumb strong {
        overflow: hidden;
        color: var(--text);
        font-size: 14px;
        font-weight: 650;
        text-overflow: ellipsis;
    }

    .status-dot {
        width: 5px;
        height: 5px;
        margin-left: 2px;
        border-radius: 50%;
        background: var(--accent);
        box-shadow: 0 0 0 3px rgba(var(--accent-rgb), .1);
    }

    .status-dot.offline {
        background: #e48652;
        box-shadow: 0 0 0 3px rgba(228, 134, 82, .11);
    }

    .window-actions {
        gap: 10px;
    }

    .traffic {
        display: block;
        width: 11px;
        height: 11px;
        padding: 0;
        border-radius: 50%;
        cursor: default;
        box-shadow: inset 0 -1px 1px rgba(0, 0, 0, .14);
    }

    button.traffic {
        cursor: pointer;
        transition: transform 140ms ease, filter 140ms ease;
    }

    button.traffic:hover {
        filter: brightness(1.08);
        transform: scale(1.14);
    }

    .traffic.red { background: #e66f74; }
    .traffic.amber { background: #e9ad4f; }
    .traffic.green { background: #45b998; }

    .theme-button {
        display: grid;
        width: 30px;
        height: 30px;
        margin-right: 3px;
        place-items: center;
        border-radius: 9px;
        color: var(--muted);
        cursor: pointer;
        background: transparent;
        transition: color 160ms ease, background 160ms ease;
    }

    .theme-button:hover {
        color: var(--accent);
        background: rgba(var(--accent-rgb), .1);
    }

    .theme-button svg {
        width: 17px;
        height: 17px;
    }

    .module-workspace,
    .config-workspace {
        display: grid;
        min-width: 0;
        min-height: 0;
        padding: 16px;
        gap: 16px;
    }

    .module-workspace {
        grid-template-columns: minmax(0, 1.16fr) minmax(300px, .84fr);
    }

    .config-workspace {
        grid-template-columns: minmax(260px, .72fr) minmax(0, 1.28fr);
    }

    .panel {
        min-width: 0;
        min-height: 0;
        overflow: hidden;
        border: 1px solid rgba(255, 255, 255, .56);
        border-radius: 18px;
        background: var(--surface);
        box-shadow:
            inset 0 1px 0 rgba(255, 255, 255, .5),
            0 13px 30px rgba(28, 32, 46, .1);
        backdrop-filter: blur(22px);
        -webkit-backdrop-filter: blur(22px);
    }

    .dark-theme .panel {
        border-color: rgba(255, 255, 255, .08);
        box-shadow:
            inset 0 1px 0 rgba(255, 255, 255, .05),
            0 13px 30px rgba(0, 0, 0, .19);
    }

    .settings-panel,
    .modules-panel,
    .config-list-panel {
        display: flex;
        min-height: 0;
        flex-direction: column;
    }

    .module-heading,
    .panel-heading {
        display: flex;
        min-height: 71px;
        padding: 16px 18px;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid var(--line);
    }

    .module-title {
        display: flex;
        min-width: 0;
        align-items: center;
        gap: 12px;
    }

    .accent-line {
        width: 3px;
        height: 31px;
        border-radius: 4px;
        background: var(--accent);
        box-shadow: 0 0 12px rgba(var(--accent-rgb), .28);
    }

    .eyebrow {
        display: block;
        margin-bottom: 4px;
        color: var(--muted);
        font-size: 9px;
        font-weight: 700;
        letter-spacing: .13em;
        text-transform: uppercase;
    }

    h1,
    h2,
    p {
        margin: 0;
    }

    h1,
    h2 {
        color: var(--text);
        font-weight: 650;
        letter-spacing: -.025em;
    }

    h1 {
        font-size: 18px;
    }

    h2 {
        font-size: 17px;
    }

    .power-button {
        display: grid;
        width: 36px;
        height: 36px;
        place-items: center;
        border: 1px solid var(--line);
        border-radius: 11px;
        color: var(--muted);
        cursor: pointer;
        background: var(--soft);
        transition: all 180ms ease;
    }

    .power-button svg {
        width: 18px;
        height: 18px;
    }

    .power-button.enabled {
        border-color: rgba(var(--accent-rgb), .28);
        color: var(--accent);
        background: rgba(var(--accent-rgb), .12);
        box-shadow: 0 7px 18px rgba(var(--accent-rgb), .15);
    }

    .module-meta {
        display: flex;
        min-height: 53px;
        padding: 0 18px;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid var(--line);
    }

    .enabled-check {
        display: flex;
        align-items: center;
        gap: 9px;
        cursor: pointer;
        font-size: 13px;
    }

    .enabled-check input,
    .multi-options input {
        position: absolute;
        opacity: 0;
        pointer-events: none;
    }

    .enabled-check > span,
    .multi-options label > span {
        display: grid;
        width: 17px;
        height: 17px;
        place-items: center;
        border: 1px solid var(--line-strong);
        border-radius: 5px;
        background: var(--surface-solid);
        transition: all 160ms ease;
    }

    .enabled-check > span::after,
    .multi-options label > span::after {
        width: 7px;
        height: 4px;
        border-bottom: 1.7px solid white;
        border-left: 1.7px solid white;
        content: "";
        opacity: 0;
        transform: translateY(-1px) rotate(-45deg) scale(.5);
        transition: all 150ms ease;
    }

    .enabled-check input:checked + span,
    .multi-options input:checked + span {
        border-color: var(--accent);
        background: var(--accent);
        box-shadow: 0 4px 10px rgba(var(--accent-rgb), .25);
    }

    .enabled-check input:checked + span::after,
    .multi-options input:checked + span::after {
        opacity: 1;
        transform: translateY(-1px) rotate(-45deg) scale(1);
    }

    .bind-button,
    .key-row button {
        display: flex;
        min-width: 78px;
        min-height: 29px;
        padding: 0 10px;
        align-items: center;
        justify-content: center;
        gap: 7px;
        border: 1px solid var(--line);
        border-radius: 8px;
        color: var(--muted);
        cursor: pointer;
        background: var(--soft);
        font-size: 10px;
        font-weight: 650;
        letter-spacing: .04em;
        text-transform: uppercase;
        transition: all 160ms ease;
    }

    .bind-button:hover,
    .key-row button:hover,
    .bind-button.binding,
    .key-row button.binding {
        border-color: rgba(var(--accent-rgb), .35);
        color: var(--accent);
        background: rgba(var(--accent-rgb), .1);
    }

    .bind-button svg {
        width: 15px;
        height: 15px;
    }

    .settings-scroll,
    .module-list,
    .config-list {
        min-height: 0;
        overflow: auto;
        scrollbar-width: thin;
        scrollbar-color: rgba(var(--accent-rgb), .36) transparent;
    }

    .settings-scroll {
        flex: 1;
        padding: 10px 24px 20px;
        overscroll-behavior: contain;
    }

    .setting-row {
        position: relative;
        display: flex;
        min-height: 58px;
        margin-bottom: 8px;
        padding: 11px 14px;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
        border: 1px solid var(--line);
        border-radius: 12px;
        background: rgba(255, 255, 255, .08);
    }

    .setting-row:last-child {
        margin-bottom: 0;
    }

    .dark-theme .setting-row {
        background: rgba(255, 255, 255, .018);
    }

    .setting-copy {
        display: flex;
        min-width: 0;
        flex-direction: column;
        gap: 3px;
    }

    .setting-copy strong,
    .setting-label-line strong {
        font-size: 12.5px;
        font-weight: 570;
    }

    .setting-copy small,
    .setting-label-line small,
    .range-bounds {
        color: var(--muted);
        font-size: 9.5px;
    }

    .boolean-row {
        cursor: pointer;
    }

    .native-toggle {
        position: absolute;
        right: 14px;
        z-index: 2;
        width: 36px;
        height: 22px;
        cursor: pointer;
        opacity: 0;
    }

    .switch-track {
        position: relative;
        width: 36px;
        height: 21px;
        border-radius: 20px;
        pointer-events: none;
        background: var(--line-strong);
        transition: background 170ms ease;
    }

    .switch-track::after {
        position: absolute;
        top: 3px;
        left: 3px;
        width: 15px;
        height: 15px;
        border-radius: 50%;
        content: "";
        background: white;
        box-shadow: 0 2px 6px rgba(0, 0, 0, .2);
        transition: transform 180ms cubic-bezier(.2, .8, .2, 1);
    }

    .native-toggle:checked + .switch-track {
        background: var(--accent);
    }

    .native-toggle:checked + .switch-track::after {
        transform: translateX(15px);
    }

    .slider-row,
    .multi-row {
        display: block;
        padding-top: 13px;
        padding-bottom: 13px;
    }

    .setting-label-line {
        display: flex;
        margin-bottom: 10px;
        align-items: center;
        justify-content: space-between;
    }

    .slider-row output {
        min-width: 40px;
        padding: 3px 7px;
        border: 1px solid var(--line);
        border-radius: 6px;
        color: var(--accent);
        background: var(--soft);
        font-size: 10px;
        font-weight: 650;
        text-align: center;
    }

    .slider-row input[type="range"] {
        width: 100%;
        height: 12px;
        margin: 0;
        outline: none;
        appearance: none;
        -webkit-appearance: none;
        background: transparent;
        cursor: pointer;
    }

    .slider-row input[type="range"]::-webkit-slider-runnable-track {
        height: 3px;
        border-radius: 3px;
        background: linear-gradient(
            90deg,
            var(--accent) 0 var(--range-progress),
            var(--line-strong) var(--range-progress) 100%
        );
    }

    .slider-row input[type="range"]::-webkit-slider-thumb {
        width: 13px;
        height: 13px;
        margin-top: -5px;
        border: 2px solid var(--surface-solid);
        border-radius: 50%;
        appearance: none;
        -webkit-appearance: none;
        background: var(--accent);
        box-shadow: 0 2px 7px rgba(var(--accent-rgb), .38);
    }

    .range-bounds {
        display: flex;
        margin-top: 3px;
        justify-content: space-between;
    }

    .select-row {
        overflow: visible;
    }

    .select-row.mode-open {
        z-index: 40;
    }

    .mode-select {
        position: relative;
        width: min(210px, 52%);
        flex: 0 0 auto;
    }

    .mode-trigger,
    .text-row > input {
        width: 100%;
        height: 32px;
        padding: 0 10px;
        border: 1px solid var(--line);
        outline: 0;
        border-radius: 8px;
        background: var(--soft);
        font-size: 11px;
        transition: border-color 160ms ease, box-shadow 160ms ease;
    }

    .mode-trigger {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 10px;
        cursor: pointer;
        text-align: left;
    }

    .mode-trigger svg {
        width: 16px;
        height: 16px;
        flex: 0 0 auto;
        color: var(--muted);
        transition: transform 170ms cubic-bezier(.2, .8, .2, 1);
    }

    .mode-select.open .mode-trigger {
        border-color: rgba(var(--accent-rgb), .52);
        box-shadow: 0 0 0 3px rgba(var(--accent-rgb), .1);
    }

    .mode-select.open .mode-trigger svg {
        color: var(--accent);
        transform: rotate(180deg);
    }

    .mode-menu {
        position: absolute;
        z-index: 60;
        top: calc(100% + 6px);
        right: 0;
        left: 0;
        max-height: 190px;
        padding: 5px;
        overflow: auto;
        border: 1px solid var(--line-strong);
        border-radius: 10px;
        background: var(--surface-solid);
        box-shadow:
            0 16px 38px rgba(18, 22, 34, .24),
            inset 0 1px 0 rgba(255, 255, 255, .16);
        scrollbar-width: thin;
        scrollbar-color: rgba(var(--accent-rgb), .35) transparent;
        animation: mode-menu-in 150ms cubic-bezier(.2, .8, .2, 1);
    }

    .mode-option {
        display: flex;
        width: 100%;
        min-height: 31px;
        padding: 0 8px;
        align-items: center;
        justify-content: space-between;
        border-radius: 7px;
        color: var(--muted);
        cursor: pointer;
        background: transparent;
        font-size: 10.5px;
        text-align: left;
        transition: color 130ms ease, background 130ms ease;
    }

    .mode-option:hover,
    .mode-option.selected {
        color: var(--text);
        background: rgba(var(--accent-rgb), .1);
    }

    .mode-option.selected {
        color: var(--accent);
        font-weight: 650;
    }

    .mode-option svg {
        width: 13px;
        height: 13px;
        flex: 0 0 auto;
        stroke-width: 2;
    }

    .mode-trigger:focus-visible,
    .text-row > input:focus,
    .input-action input:focus {
        border-color: rgba(var(--accent-rgb), .52);
        box-shadow: 0 0 0 3px rgba(var(--accent-rgb), .1);
    }

    .multi-options {
        display: flex;
        flex-wrap: wrap;
        gap: 7px;
    }

    .multi-options label {
        display: flex;
        padding: 6px 9px;
        align-items: center;
        gap: 6px;
        border: 1px solid var(--line);
        border-radius: 8px;
        color: var(--muted);
        cursor: pointer;
        background: var(--soft);
        font-size: 10px;
    }

    .multi-options label > span {
        width: 14px;
        height: 14px;
        border-radius: 4px;
    }

    .color-control {
        display: grid;
        width: 54px;
        height: 31px;
        padding: 3px;
        place-items: center;
        border: 1px solid var(--line);
        border-radius: 8px;
        background: var(--soft);
    }

    .color-control input {
        width: 100%;
        height: 100%;
        padding: 0;
        overflow: hidden;
        border: 0;
        border-radius: 5px;
        cursor: pointer;
        background: transparent;
    }

    .text-row > input {
        width: min(210px, 52%);
        padding-right: 10px;
    }

    .empty-settings {
        display: flex;
        height: 100%;
        min-height: 230px;
        align-items: center;
        justify-content: center;
        flex-direction: column;
        color: var(--muted);
        text-align: center;
    }

    .empty-settings svg {
        width: 35px;
        height: 35px;
        margin-bottom: 12px;
        color: var(--faint);
    }

    .empty-settings strong {
        margin-bottom: 5px;
        color: var(--text);
        font-size: 13px;
    }

    .empty-settings span {
        font-size: 10px;
    }

    .panel-heading {
        min-height: 71px;
    }

    .count-badge {
        display: grid;
        min-width: 28px;
        height: 25px;
        padding: 0 8px;
        place-items: center;
        border: 1px solid rgba(var(--accent-rgb), .2);
        border-radius: 8px;
        color: var(--accent);
        background: rgba(var(--accent-rgb), .09);
        font-size: 10px;
        font-weight: 700;
    }

    .search-box {
        display: flex;
        height: 38px;
        margin: 11px 13px 7px;
        padding: 0 10px;
        flex: 0 0 auto;
        align-items: center;
        gap: 8px;
        border: 1px solid var(--line);
        border-radius: 10px;
        background: var(--soft);
    }

    .search-box svg {
        width: 15px;
        height: 15px;
        color: var(--muted);
    }

    .search-box input {
        min-width: 0;
        flex: 1;
        border: 0;
        outline: 0;
        background: transparent;
        font-size: 11px;
    }

    .search-box input::placeholder {
        color: var(--faint);
    }

    .search-box button {
        padding: 2px;
        color: var(--muted);
        cursor: pointer;
        background: transparent;
        font-size: 17px;
        line-height: 1;
    }

    .module-list {
        flex: 1;
        padding: 3px 13px 13px;
    }

    .module-item {
        position: relative;
        display: grid;
        width: 100%;
        min-height: 48px;
        padding: 7px 10px;
        grid-template-columns: 18px minmax(0, 1fr) 18px;
        align-items: center;
        column-gap: 11px;
        border-bottom: 1px solid var(--line);
        cursor: pointer;
        background: transparent;
        text-align: left;
        transition: background 150ms ease, transform 150ms ease;
    }

    .module-item:hover,
    .module-item.active {
        border-radius: 10px;
        background: rgba(var(--accent-rgb), .08);
    }

    .module-item.active::before {
        position: absolute;
        top: 12px;
        bottom: 12px;
        left: 0;
        width: 2px;
        border-radius: 2px;
        content: "";
        background: var(--accent);
    }

    .module-checkbox {
        display: grid;
        width: 18px;
        height: 18px;
        margin: 0;
        padding: 0;
        flex: 0 0 auto;
        align-self: center;
        justify-self: center;
        place-items: center;
        border: 1px solid var(--line-strong);
        border-radius: 5px;
        appearance: none;
        -webkit-appearance: none;
        line-height: 0;
        overflow: hidden;
        background: var(--surface-solid);
        transition: all 150ms ease;
    }

    .module-checkbox.checked {
        border-color: var(--accent);
        color: white;
        background: var(--accent);
        box-shadow: 0 4px 9px rgba(var(--accent-rgb), .25);
    }

    .module-checkbox svg {
        width: 13px;
        height: 13px;
        margin: auto;
        stroke-width: 2;
    }

    .module-item-copy {
        display: flex;
        min-width: 0;
        flex: 1;
        flex-direction: column;
        gap: 2px;
    }

    .module-item-copy strong {
        overflow: hidden;
        font-size: 12.5px;
        font-weight: 560;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .module-item-copy small {
        color: var(--muted);
        font-size: 9px;
    }

    .module-item.enabled .module-item-copy strong {
        color: var(--text);
    }

    .module-gear {
        display: grid;
        align-self: center;
        justify-self: center;
        place-items: center;
        color: var(--faint);
    }

    .module-gear svg {
        width: 15px;
        height: 15px;
    }

    .module-item.active .module-gear,
    .module-item:hover .module-gear {
        color: var(--accent);
    }

    .no-results {
        padding: 38px 12px;
        color: var(--muted);
        font-size: 11px;
        text-align: center;
    }

    .config-list {
        flex: 1;
        padding: 12px;
    }

    .config-item {
        display: flex;
        width: 100%;
        min-height: 60px;
        padding: 9px;
        align-items: center;
        gap: 10px;
        border: 1px solid transparent;
        border-radius: 12px;
        cursor: pointer;
        background: transparent;
        text-align: left;
        transition: all 160ms ease;
    }

    .config-item:hover,
    .config-item.active {
        border-color: var(--line);
        background: var(--soft);
    }

    .config-item.active {
        border-color: rgba(var(--accent-rgb), .24);
        box-shadow: inset 3px 0 var(--accent);
    }

    .config-icon {
        display: grid;
        width: 35px;
        height: 35px;
        flex: 0 0 auto;
        place-items: center;
        border-radius: 10px;
        color: var(--accent);
        background: rgba(var(--accent-rgb), .1);
    }

    .config-icon svg {
        width: 18px;
        height: 18px;
    }

    .config-item > span:nth-child(2) {
        display: flex;
        min-width: 0;
        flex: 1;
        flex-direction: column;
        gap: 3px;
    }

    .config-item strong {
        font-size: 12px;
        font-weight: 590;
    }

    .config-item small {
        color: var(--muted);
        font-size: 9px;
    }

    .active-pill {
        padding: 3px 6px;
        border-radius: 5px;
        color: #21a182;
        background: rgba(33, 161, 130, .1);
        font-size: 8px;
        font-weight: 750;
        letter-spacing: .06em;
    }

    .config-actions-panel {
        display: flex;
        padding: 26px;
        flex-direction: column;
    }

    .config-hero {
        display: flex;
        padding: 4px 0 25px;
        align-items: center;
        gap: 16px;
        border-bottom: 1px solid var(--line);
    }

    .config-hero-icon {
        display: grid;
        width: 55px;
        height: 55px;
        flex: 0 0 auto;
        place-items: center;
        border: 1px solid rgba(var(--accent-rgb), .2);
        border-radius: 16px;
        color: var(--accent);
        background: rgba(var(--accent-rgb), .1);
        box-shadow: 0 10px 24px rgba(var(--accent-rgb), .13);
    }

    .config-hero-icon svg {
        width: 27px;
        height: 27px;
    }

    .config-hero h2 {
        margin-bottom: 6px;
        font-size: 20px;
    }

    .config-hero p {
        color: var(--muted);
        font-size: 10px;
        line-height: 1.6;
    }

    .config-create {
        padding: 24px 0;
        border-bottom: 1px solid var(--line);
    }

    .config-create > label {
        display: block;
        margin-bottom: 8px;
        font-size: 11px;
        font-weight: 600;
    }

    .input-action {
        display: flex;
        gap: 9px;
    }

    .input-action input {
        min-width: 0;
        height: 39px;
        padding: 0 12px;
        flex: 1;
        border: 1px solid var(--line);
        outline: 0;
        border-radius: 10px;
        background: var(--soft);
        font-size: 11px;
    }

    .input-action button {
        min-width: 84px;
        border-radius: 10px;
        color: white;
        cursor: pointer;
        background: var(--accent);
        box-shadow: 0 7px 16px rgba(var(--accent-rgb), .2);
        font-size: 11px;
        font-weight: 650;
    }

    .config-action-grid {
        display: grid;
        padding-top: 23px;
        grid-template-columns: repeat(3, 1fr);
        gap: 10px;
    }

    .config-action-grid button {
        display: flex;
        min-height: 77px;
        padding: 13px;
        align-items: center;
        gap: 10px;
        border: 1px solid var(--line);
        border-radius: 12px;
        cursor: pointer;
        background: var(--soft);
        text-align: left;
        transition: transform 160ms ease, border-color 160ms ease, background 160ms ease;
    }

    .config-action-grid button:hover:not(:disabled) {
        border-color: rgba(var(--accent-rgb), .3);
        background: rgba(var(--accent-rgb), .08);
        transform: translateY(-2px);
    }

    .config-action-grid button.danger:hover:not(:disabled) {
        border-color: rgba(220, 86, 93, .28);
        color: #d9535f;
        background: rgba(220, 86, 93, .08);
    }

    .config-action-grid svg {
        width: 21px;
        height: 21px;
        flex: 0 0 auto;
        color: var(--accent);
    }

    .config-action-grid .danger svg {
        color: #d9535f;
    }

    .config-action-grid span {
        display: flex;
        min-width: 0;
        flex-direction: column;
        gap: 4px;
    }

    .config-action-grid strong {
        font-size: 11px;
    }

    .config-action-grid small {
        color: var(--muted);
        font-size: 8.5px;
        line-height: 1.35;
    }

    button:disabled,
    input:disabled {
        cursor: default;
        opacity: .5;
    }

    .statusbar {
        display: flex;
        padding: 0 18px;
        align-items: center;
        justify-content: space-between;
        border-top: 1px solid var(--line);
        color: var(--muted);
        background: rgba(255, 255, 255, .22);
        font-size: 9px;
        letter-spacing: .025em;
    }

    .dark-theme .statusbar {
        background: rgba(255, 255, 255, .02);
    }

    .statusbar > div {
        display: flex;
        gap: 21px;
    }

    .statusbar strong {
        color: var(--accent);
        font-weight: 700;
    }

    .protected {
        display: flex;
        align-items: center;
        gap: 7px;
        color: #29a78a;
        font-weight: 650;
    }

    .protected i {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #29b394;
        box-shadow: 0 0 0 3px rgba(41, 179, 148, .1);
    }

    .loading-state {
        display: flex;
        align-items: center;
        justify-content: center;
        flex-direction: column;
        color: var(--muted);
        font-size: 11px;
    }

    .loading-state.error strong {
        margin-bottom: 6px;
        color: #d85e68;
        font-size: 13px;
    }

    .loading-state button {
        margin-top: 13px;
        padding: 7px 15px;
        border-radius: 8px;
        color: white;
        cursor: pointer;
        background: var(--accent);
        font-size: 10px;
    }

    .loader {
        width: 22px;
        height: 22px;
        margin-bottom: 12px;
        border: 2px solid var(--line-strong);
        border-top-color: var(--accent);
        border-radius: 50%;
        animation: spin .7s linear infinite;
    }

    .toast {
        position: absolute;
        z-index: 20;
        right: max(28px, calc((100vw - min(1160px, calc(100vw - 44px))) / 2 + 18px));
        bottom: max(28px, calc((100vh - min(735px, calc(100vh - 44px))) / 2 + 38px));
        padding: 9px 13px;
        border: 1px solid rgba(42, 166, 137, .2);
        border-radius: 9px;
        color: #218f77;
        background: var(--surface-solid);
        box-shadow: 0 12px 32px var(--shadow);
        font-size: 10px;
        font-weight: 600;
        animation: toast-in 220ms cubic-bezier(.2, .8, .2, 1);
    }

    .toast.error {
        border-color: rgba(216, 82, 94, .2);
        color: #d5525e;
    }

    @keyframes spin {
        to { transform: rotate(360deg); }
    }

    @keyframes toast-in {
        from { opacity: 0; transform: translateY(8px) scale(.96); }
    }

    @keyframes mode-menu-in {
        from {
            opacity: 0;
            transform: translateY(-4px) scale(.98);
        }
    }

    @media (max-width: 900px) {
        .clickgui-screen {
            --screen-pad-x: 18px;
            --screen-pad-y: 18px;
        }

        .window-shell {
            grid-template-columns: 64px minmax(0, 1fr);
            border-radius: 23px;
        }

        .sidebar {
            padding-inline: 8px;
        }

        .module-workspace,
        .config-workspace {
            grid-template-columns: minmax(0, 1.1fr) minmax(250px, .9fr);
            gap: 11px;
            padding: 11px;
        }

        .setting-row {
            gap: 8px;
        }

        .config-action-grid {
            grid-template-columns: 1fr;
        }

        .config-action-grid button {
            min-height: 57px;
        }
    }

    @media (max-height: 600px) {
        .clickgui-screen {
            --screen-pad-x: 14px;
            --screen-pad-y: 14px;
        }

        .window-shell {
            border-radius: 22px;
        }

        .sidebar {
            padding-top: 12px;
            padding-bottom: 10px;
        }

        .brand-button {
            width: 42px;
            height: 42px;
            margin-bottom: 8px;
        }

        .category-nav {
            gap: 2px;
        }

        .nav-button {
            height: 38px;
        }

        .app-column {
            grid-template-rows: 49px minmax(0, 1fr) 24px;
        }

        .module-workspace,
        .config-workspace {
            padding: 10px;
            gap: 10px;
        }

        .module-heading,
        .panel-heading {
            min-height: 58px;
            padding-block: 10px;
        }

        .module-meta {
            min-height: 44px;
        }

        .setting-row {
            min-height: 50px;
            padding-block: 8px;
        }
    }
</style>
