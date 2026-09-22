<script lang="ts">
    import {onMount} from "svelte";
    import ScriptPage from "./ScriptPage.svelte";
    import ScriptScreen from "./ScriptScreen.svelte";
    import ClickGuiScreen from "./ClickGuiScreen.svelte";

    function currentRoute() {
        return location.hash.replace(/^#\/?/, "").split(/[/?]/, 1)[0] || "clickgui";
    }

    let route = currentRoute();

    onMount(() => {
        const onHashChange = () => route = currentRoute();
        window.addEventListener("hashchange", onHashChange);
        return () => window.removeEventListener("hashchange", onHashChange);
    });
</script>

{#if route === "clickgui"}
    <ClickGuiScreen />
{:else if route === "script-page"}
    <ScriptPage />
{:else if route === "scripts"}
    <ScriptScreen />
{:else}
    <p>AI 已迁移到独立的 DeepSeek Harness 界面。请在游戏内打开 AIScreen。</p>
{/if}
