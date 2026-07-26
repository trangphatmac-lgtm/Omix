<script lang="ts">
    import {onMount, tick} from "svelte";
    import {fly} from "svelte/transition";
    import {quintIn, quintOut} from "svelte/easing";
    import remixLogo from "./assets/remix.png";

    type Mode = "chat" | "agent";
    type Role = "user" | "assistant";
    type View = "conversation" | "settings";
    type Theme = "light" | "dark";

    const APPEARANCE_STORAGE_KEY = "ai.appearance.v1";

    interface AppearanceSettings {
        theme: Theme;
        blur: boolean;
        opacity: number;
    }

    interface ToolActivity {
        id: string;
        name: string;
        arguments: string;
        result: string;
        status: "running" | "complete";
    }

    interface Message {
        id: string;
        role: Role;
        content: string;
        reasoning: string;
        tools: ToolActivity[];
        waiting: boolean;
        error: boolean;
    }

    interface StoredToolCall {
        id?: string;
        function?: {
            name?: string;
            arguments?: string;
        };
    }

    interface StoredAiMessage {
        role?: "user" | "assistant" | "tool";
        content?: string;
        reasoning_content?: string;
        tool_calls?: StoredToolCall[];
        tool_call_id?: string;
    }

    interface AiConfiguration {
        baseUrl: string;
        hasApiKey: boolean;
        model: string;
        thinking: boolean;
        active: boolean;
        history: Record<Mode, number>;
        models: string[];
    }

    let mode: Mode = "chat";
    let view: View = "conversation";
    let mounted = false;
    let closing = false;
    let sending = false;
    let draft = "";
    let composing = false;
    let socket: WebSocket | null = null;
    let connectionState: "connecting" | "ready" | "offline" = "connecting";
    let conversations: Record<Mode, Message[]> = {chat: [], agent: []};
    let conversationNode: HTMLDivElement;
    let inputNode: HTMLTextAreaElement;
    let activeRequestId = "";
    let settingsBusy = false;
    let settingsMessage = "";
    let apiKeyDraft = "";
    let clearApiKey = false;
    let appearance: AppearanceSettings = {
        theme: "light",
        blur: true,
        opacity: .54
    };
    let config: AiConfiguration = {
        baseUrl: "",
        hasApiKey: false,
        model: "",
        thinking: true,
        active: false,
        history: {chat: 0, agent: 0},
        models: []
    };

    onMount(() => {
        const firstFrame = requestAnimationFrame(() => {
            requestAnimationFrame(() => mounted = true);
        });
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                event.preventDefault();
                beginClose();
            }
        };
        window.addEventListener("keydown", onKeyDown);
        connectSocket();
        void acknowledgeScreen();
        void loadConfiguration();
        void loadConversations();
        void loadAppearance();

        return () => {
            cancelAnimationFrame(firstFrame);
            window.removeEventListener("keydown", onKeyDown);
            socket?.close();
        };
    });

    function connectSocket() {
        const scheme = location.protocol === "https:" ? "wss" : "ws";
        socket = new WebSocket(`${scheme}://${location.host}/ws`);
        socket.addEventListener("open", () => connectionState = "ready");
        socket.addEventListener("close", () => {
            connectionState = "offline";
            if (sending) {
                failActiveMessage("连接已断开，请重新打开页面后再试。");
            }
        });
        socket.addEventListener("error", () => connectionState = "offline");
        socket.addEventListener("message", event => {
            const packet = JSON.parse(String(event.data)) as {
                name?: string;
                event?: {
                    requestId?: string;
                    content?: string;
                    route?: string;
                    toolCallId?: string;
                    toolName?: string;
                    arguments?: string;
                };
            };
            const payload = packet.event ?? {};
            if (packet.name === "screenClosing" && payload.route === "ai") {
                beginClose();
                return;
            }
            if (!payload.requestId || payload.requestId !== activeRequestId) {
                return;
            }
            if (packet.name === "aiDelta") {
                updateAssistant(payload.requestId, message => ({
                    ...message,
                    content: message.content + (payload.content ?? ""),
                    waiting: false
                }));
            } else if (packet.name === "aiReasoning") {
                updateAssistant(payload.requestId, message => ({
                    ...message,
                    reasoning: message.reasoning + (payload.content ?? "")
                }));
            } else if (packet.name === "aiToolCall" && payload.toolCallId) {
                updateAssistant(payload.requestId, message => ({
                    ...message,
                    tools: [
                        ...message.tools,
                        {
                            id: payload.toolCallId!,
                            name: payload.toolName ?? "tool",
                            arguments: payload.arguments ?? "{}",
                            result: "",
                            status: "running"
                        }
                    ]
                }));
            } else if (packet.name === "aiToolResult" && payload.toolCallId) {
                updateAssistant(payload.requestId, message => ({
                    ...message,
                    tools: message.tools.map(tool => tool.id === payload.toolCallId
                        ? {
                            ...tool,
                            result: payload.content ?? "",
                            status: "complete"
                        }
                        : tool)
                }));
            } else if (packet.name === "aiComplete") {
                updateAssistant(payload.requestId, message => ({
                    ...message,
                    content: message.content || payload.content || "（空响应）",
                    waiting: false
                }));
                finishRequest();
            } else if (packet.name === "aiError") {
                failActiveMessage(payload.content || "AI 请求失败。");
            }
        });
    }

    function beginClose() {
        if (!closing) {
            closing = true;
            mounted = false;
        }
    }

    async function acknowledgeScreen() {
        for (let attempt = 0; attempt < 40; attempt++) {
            try {
                const response = await post("/api/v1/client/virtualScreen", {name: "ai"});
                if (response.ok) return;
            } catch {
                // The local bridge can still be starting while CEF paints its first frame.
            }
            await new Promise(resolve => setTimeout(resolve, 250));
        }
    }

    async function post(path: string, body: unknown = {}) {
        return fetch(path, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(body)
        });
    }

    async function loadConfiguration() {
        try {
            const response = await fetch("/api/v1/ai/config");
            config = await readConfiguration(response);
            settingsMessage = "";
        } catch (error) {
            settingsMessage = error instanceof Error ? error.message : String(error);
        }
    }

    async function loadConversations() {
        const restored = await Promise.all((["chat", "agent"] as const).map(async targetMode => {
            try {
                const response = await fetch(
                    `/api/v1/ai/conversation?mode=${encodeURIComponent(targetMode)}`
                );
                if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) {
                    return null;
                }
                const body = await response.json() as {messages?: StoredAiMessage[]};
                return [targetMode, restoreConversation(targetMode, body.messages ?? [])] as const;
            } catch {
                return null;
            }
        }));

        let next = conversations;
        for (const entry of restored) {
            if (entry) {
                next = {...next, [entry[0]]: entry[1]};
            }
        }
        conversations = next;
        void scrollToBottom();
    }

    function restoreConversation(targetMode: Mode, stored: StoredAiMessage[]) {
        const restored: Message[] = [];
        let assistant: Message | null = null;

        stored.forEach((entry, index) => {
            if (entry.role === "user") {
                restored.push({
                    id: `${targetMode}:history:${index}:user`,
                    role: "user",
                    content: entry.content ?? "",
                    reasoning: "",
                    tools: [],
                    waiting: false,
                    error: false
                });
                assistant = null;
                return;
            }
            if (entry.role !== "assistant" && entry.role !== "tool") {
                return;
            }
            if (!assistant) {
                assistant = {
                    id: `${targetMode}:history:${index}:assistant`,
                    role: "assistant",
                    content: "",
                    reasoning: "",
                    tools: [],
                    waiting: false,
                    error: false
                };
                restored.push(assistant);
            }
            if (entry.role === "assistant") {
                assistant.content = appendHistoryPart(assistant.content, entry.content ?? "");
                assistant.reasoning = appendHistoryPart(
                    assistant.reasoning,
                    entry.reasoning_content ?? ""
                );
                for (const call of entry.tool_calls ?? []) {
                    if (!call.id || assistant.tools.some(tool => tool.id === call.id)) continue;
                    assistant.tools.push({
                        id: call.id,
                        name: call.function?.name ?? "tool",
                        arguments: call.function?.arguments ?? "{}",
                        result: "",
                        status: "running"
                    });
                }
                return;
            }

            const tool = assistant.tools.find(activity => activity.id === entry.tool_call_id);
            if (tool) {
                tool.result = entry.content ?? "";
                tool.status = "complete";
            }
        });

        return restored;
    }

    function appendHistoryPart(current: string, next: string) {
        if (!next) return current;
        return current ? `${current}\n${next}` : next;
    }

    async function loadAppearance() {
        try {
            const response = await fetch(
                `/api/v1/client/localStorage?key=${encodeURIComponent(APPEARANCE_STORAGE_KEY)}`
            );
            if (!response.ok) return;
            const body = await response.json() as {value?: Partial<AppearanceSettings>};
            const stored = body.value ?? {};
            appearance = {
                theme: stored.theme === "dark" ? "dark" : "light",
                blur: typeof stored.blur === "boolean" ? stored.blur : true,
                opacity: typeof stored.opacity === "number"
                    ? Math.min(.9, Math.max(.2, stored.opacity))
                    : .54
            };
            void applyBackgroundBlur();
        } catch {
            // The appearance bridge is only available inside the in-game WebUI.
        }
    }

    async function persistAppearance() {
        try {
            const response = await fetch("/api/v1/client/localStorage", {
                method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({key: APPEARANCE_STORAGE_KEY, value: appearance})
            });
            if (!response.ok) throw new Error(await response.text());
        } catch (error) {
            if (view === "settings") {
                settingsMessage = error instanceof Error ? error.message : String(error);
            }
        }
    }

    function selectTheme(theme: Theme) {
        appearance = {...appearance, theme};
        void persistAppearance();
    }

    function toggleBackgroundBlur() {
        appearance = {...appearance, blur: !appearance.blur};
        void applyBackgroundBlur();
        void persistAppearance();
    }

    async function applyBackgroundBlur() {
        try {
            await fetch("/api/v1/client/backgroundBlur", {
                method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({enabled: appearance.blur})
            });
        } catch {
            // CSS still removes the glass blur when running outside Minecraft.
        }
    }

    function changeOpacity(event: Event) {
        const opacity = Number((event.currentTarget as HTMLInputElement).value);
        appearance = {...appearance, opacity};
    }

    async function readConfiguration(response: Response) {
        if (!response.ok) throw new Error(await response.text());
        if (!response.headers.get("content-type")?.includes("application/json")) {
            throw new Error("AIbackend 仅在游戏内 WebUI 中可用");
        }
        return await response.json() as AiConfiguration;
    }

    function selectMode(next: Mode) {
        if (sending) return;
        view = "conversation";
        if (next === mode) return;
        mode = next;
        void scrollToBottom();
    }

    async function sendMessage() {
        const content = draft.trim();
        if (!content || sending) return;
        if (!socket || socket.readyState !== WebSocket.OPEN) {
            connectionState = "offline";
            return;
        }

        const requestId = globalThis.crypto?.randomUUID?.()
            ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
        activeRequestId = requestId;
        sending = true;
        const targetMode = mode;
        conversations = {
            ...conversations,
            [targetMode]: [
                ...conversations[targetMode],
                {
                    id: `${requestId}:user`,
                    role: "user",
                    content,
                    reasoning: "",
                    tools: [],
                    waiting: false,
                    error: false
                },
                {
                    id: requestId,
                    role: "assistant",
                    content: "",
                    reasoning: "",
                    tools: [],
                    waiting: true,
                    error: false
                }
            ]
        };
        draft = "";
        await tick();
        if (inputNode) inputNode.style.height = "";
        await scrollToBottom();
        socket.send(JSON.stringify({
            name: "aiChat",
            event: {requestId, message: content, mode: targetMode}
        }));
    }

    function updateAssistant(requestId: string, update: (message: Message) => Message) {
        for (const targetMode of ["chat", "agent"] as const) {
            const index = conversations[targetMode].findIndex(message => message.id === requestId);
            if (index < 0) continue;
            const messages = [...conversations[targetMode]];
            messages[index] = update(messages[index]);
            conversations = {...conversations, [targetMode]: messages};
            void scrollToBottom();
            return;
        }
    }

    function failActiveMessage(content: string) {
        updateAssistant(activeRequestId, message => ({
            ...message,
            content,
            waiting: false,
            error: true
        }));
        finishRequest();
    }

    function finishRequest() {
        sending = false;
        activeRequestId = "";
        void loadConfiguration();
    }

    async function scrollToBottom() {
        await tick();
        conversationNode?.scrollTo({top: conversationNode.scrollHeight, behavior: "smooth"});
    }

    function handleInputKeydown(event: KeyboardEvent) {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing && !composing) {
            event.preventDefault();
            void sendMessage();
        }
    }

    function resizeInput(event: Event) {
        const textarea = event.currentTarget as HTMLTextAreaElement;
        textarea.style.height = "0";
        textarea.style.height = `${Math.min(textarea.scrollHeight, 144)}px`;
    }

    function openSettings() {
        view = "settings";
        settingsMessage = "";
        void loadConfiguration();
    }

    function toolLabel(name: string) {
        if (name === "run_minecraft_command") return "Minecraft";
        if (name === "run_client_command") return "Remix";
        if (name === "run_baritone_command") return "Baritone";
        return name;
    }

    function toolCommand(argumentsJson: string) {
        try {
            const parsed = JSON.parse(argumentsJson) as {command?: unknown};
            return typeof parsed.command === "string" ? parsed.command : argumentsJson;
        } catch {
            return argumentsJson;
        }
    }

    async function saveConfiguration() {
        settingsBusy = true;
        settingsMessage = "";
        const body: Record<string, unknown> = {
            baseUrl: config.baseUrl,
            model: config.model,
            thinking: config.thinking
        };
        if (apiKeyDraft || clearApiKey) {
            body.apiKey = clearApiKey ? "" : apiKeyDraft;
        }
        try {
            const response = await fetch("/api/v1/ai/config", {
                method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(body)
            });
            config = await readConfiguration(response);
            apiKeyDraft = "";
            clearApiKey = false;
            settingsMessage = "设置已保存";
        } catch (error) {
            settingsMessage = error instanceof Error ? error.message : String(error);
        } finally {
            settingsBusy = false;
        }
    }

    async function clearConversation() {
        if (sending) return;
        settingsBusy = true;
        settingsMessage = "";
        try {
            const response = await fetch(
                `/api/v1/ai/conversation?mode=${encodeURIComponent(mode)}`,
                {method: "DELETE"}
            );
            if (!response.ok) throw new Error(await response.text());
            conversations = {...conversations, [mode]: []};
            await loadConfiguration();
            settingsMessage = `${mode === "chat" ? "Chat" : "Agent"} 上下文已清空`;
        } catch (error) {
            settingsMessage = error instanceof Error ? error.message : String(error);
        } finally {
            settingsBusy = false;
        }
    }
</script>

<main
    class:mounted
    class:closing
    class:dark-theme={appearance.theme === "dark"}
    class:no-blur={!appearance.blur}
    class="ai-screen"
    style={`--surface-alpha:${appearance.opacity};--surface-strong-alpha:${Math.min(.96, appearance.opacity + .18)}`}
    aria-label="AI 助手"
>
    {#if view === "conversation"}
        <div class="mode-switcher" role="group" aria-label="AI 模式">
            <span class:agent={mode === "agent"} class="mode-indicator" aria-hidden="true"></span>
            <button
                class:active={mode === "chat"}
                class="mode-button"
                type="button"
                aria-pressed={mode === "chat"}
                disabled={sending}
                onclick={() => selectMode("chat")}
            >
                <span>Chat</span>
            </button>
            <button
                class:active={mode === "agent"}
                class="mode-button"
                type="button"
                aria-pressed={mode === "agent"}
                disabled={sending}
                onclick={() => selectMode("agent")}
            >
                <span>Agent</span>
            </button>
        </div>
    {/if}

    {#key `${view}-${mode}`}
        {#if view === "conversation"}
            <section
                class="workspace"
                aria-label={mode === "chat" ? "Chat 对话" : "Agent 对话"}
                in:fly={{x: mode === "chat" ? -54 : 54, duration: 430, easing: quintOut}}
                out:fly={{x: mode === "chat" ? 54 : -54, duration: 250, easing: quintIn}}
            >
                <div class="conversation glass" bind:this={conversationNode}>
                    {#if conversations[mode].length === 0}
                        <div class="empty-state">
                            <div class="mode-mark" aria-hidden="true">
                                <img src={remixLogo} alt="" />
                            </div>
                            <h1>{mode === "chat" ? "开始一段对话" : "让 Agent 执行任务"}</h1>
                        </div>
                    {:else}
                        <div class="messages" aria-live="polite">
                            {#each conversations[mode] as message (message.id)}
                                <article class:user={message.role === "user"} class:error={message.error} class="message">
                                    <span class="message-role">{message.role === "user" ? "你" : mode}</span>
                                    {#if message.reasoning}
                                        <details class="reasoning">
                                            <summary>思考过程</summary>
                                            <p>{message.reasoning}</p>
                                        </details>
                                    {/if}
                                    {#if message.tools.length > 0}
                                        <div class="tool-list">
                                            {#each message.tools as tool (tool.id)}
                                                <details class:running={tool.status === "running"} class="tool-card" open>
                                                    <summary>
                                                        <span class="tool-status"></span>
                                                        <strong>{toolLabel(tool.name)}</strong>
                                                        <code>{toolCommand(tool.arguments)}</code>
                                                    </summary>
                                                    {#if tool.result}
                                                        <pre>{tool.result}</pre>
                                                    {/if}
                                                </details>
                                            {/each}
                                        </div>
                                    {/if}
                                    {#if message.waiting && !message.content}
                                        <span class="thinking" aria-label="正在生成">
                                            <i></i><i></i><i></i>
                                        </span>
                                    {:else}
                                        <p class="message-content">{message.content}</p>
                                    {/if}
                                </article>
                            {/each}
                        </div>
                    {/if}

                    <button
                        class="icon-button settings-button"
                        type="button"
                        aria-label="AI 设置"
                        title="设置"
                        onclick={openSettings}
                    >
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M4 7h10M18 7h2M4 17h2M10 17h10M14 4v6M8 14v6" />
                            <circle cx="14" cy="7" r="2" />
                            <circle cx="8" cy="17" r="2" />
                        </svg>
                    </button>
                </div>

                <div class="composer glass">
                    <textarea
                        bind:this={inputNode}
                        bind:value={draft}
                        rows="1"
                        aria-label="输入消息"
                        placeholder={connectionState === "offline" ? "AIbackend 连接不可用" : "输入消息…"}
                        disabled={connectionState === "offline"}
                        oninput={resizeInput}
                        onkeydown={handleInputKeydown}
                        oncompositionstart={() => composing = true}
                        oncompositionend={() => composing = false}
                    ></textarea>
                    <button
                        class="icon-button send-button"
                        type="button"
                        aria-label="发送消息"
                        title="发送"
                        disabled={!draft.trim() || sending || connectionState !== "ready"}
                        onclick={sendMessage}
                    >
                        {#if sending}
                            <span class="send-spinner"></span>
                        {:else}
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                                <path d="M5 12h13M13 6l6 6-6 6" />
                            </svg>
                        {/if}
                    </button>
                </div>
            </section>
        {:else}
            <section
                class="settings-screen glass"
                aria-label="AI 设置页面"
                in:fly={{x: 70, duration: 430, easing: quintOut}}
                out:fly={{x: 70, duration: 250, easing: quintIn}}
            >
                <header class="settings-header">
                    <button
                        class="icon-button back-button"
                        type="button"
                        aria-label="返回对话"
                        onclick={() => view = "conversation"}
                    >
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M19 12H5M11 6l-6 6 6 6" />
                        </svg>
                    </button>
                    <div>
                        <span class="panel-kicker">AI SCREEN</span>
                        <h2>设置</h2>
                    </div>
                </header>

                <section class="settings-section" aria-labelledby="appearance-title">
                    <div class="section-heading">
                        <div>
                            <span class="panel-kicker">APPEARANCE</span>
                            <h3 id="appearance-title">界面外观</h3>
                        </div>
                        <span>{Math.round(appearance.opacity * 100)}%</span>
                    </div>
                    <div class="theme-selector" role="group" aria-label="颜色模式">
                        <span class:dark={appearance.theme === "dark"} aria-hidden="true"></span>
                        <button
                            class:active={appearance.theme === "light"}
                            type="button"
                            aria-pressed={appearance.theme === "light"}
                            onclick={() => selectTheme("light")}
                        >浅色</button>
                        <button
                            class:active={appearance.theme === "dark"}
                            type="button"
                            aria-pressed={appearance.theme === "dark"}
                            onclick={() => selectTheme("dark")}
                        >深色</button>
                    </div>
                    <div class="appearance-grid">
                        <button
                            class:active={appearance.blur}
                            class="setting-row"
                            type="button"
                            role="switch"
                            aria-checked={appearance.blur}
                            onclick={toggleBackgroundBlur}
                        >
                            <span>背景高斯模糊</span>
                            <i><b></b></i>
                        </button>
                        <label class="opacity-control">
                            <span>页面透明度</span>
                            <input
                                type="range"
                                min=".2"
                                max=".9"
                                step=".01"
                                value={appearance.opacity}
                                oninput={changeOpacity}
                                onchange={() => void persistAppearance()}
                            />
                        </label>
                    </div>
                </section>

                <div class="section-heading backend-heading">
                    <div>
                        <span class="panel-kicker">AI BACKEND</span>
                        <h3>连接</h3>
                    </div>
                </div>
                <div class="settings-grid">
                    <label>
                        <span>API 地址</span>
                        <input bind:value={config.baseUrl} autocomplete="url" />
                    </label>
                    <div class="api-key-field">
                        <label>
                            <span>API Key</span>
                            <input
                                bind:value={apiKeyDraft}
                                type="password"
                                autocomplete="off"
                                placeholder={config.hasApiKey ? "已配置 · 留空保持不变" : "输入 API Key"}
                            />
                        </label>
                        {#if config.hasApiKey}
                            <button
                                class:active={clearApiKey}
                                class="clear-key"
                                type="button"
                                aria-pressed={clearApiKey}
                                onclick={() => clearApiKey = !clearApiKey}
                            >
                                <span></span>清除已有密钥
                            </button>
                        {/if}
                    </div>
                    <label>
                        <span>模型</span>
                        <input bind:value={config.model} list="ai-models" autocomplete="off" />
                        <datalist id="ai-models">
                            {#each config.models as model}
                                <option value={model}></option>
                            {/each}
                        </datalist>
                    </label>
                    <button
                        class:active={config.thinking}
                        class="setting-row"
                        type="button"
                        role="switch"
                        aria-checked={config.thinking}
                        onclick={() => config = {...config, thinking: !config.thinking}}
                    >
                        <span>Thinking</span>
                        <i><b></b></i>
                    </button>
                </div>
                <div class="settings-actions">
                    <button type="button" disabled={settingsBusy || sending} onclick={clearConversation}>
                        清空 {mode === "chat" ? "Chat" : "Agent"}
                    </button>
                    <button class="primary" type="button" disabled={settingsBusy} onclick={saveConfiguration}>
                        {settingsBusy ? "保存中…" : "保存"}
                    </button>
                </div>
                {#if settingsMessage}
                    <p class="settings-message">{settingsMessage}</p>
                {/if}
            </section>
        {/if}
    {/key}
</main>
