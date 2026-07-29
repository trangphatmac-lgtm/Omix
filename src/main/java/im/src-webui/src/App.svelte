<script lang="ts">
    import {onMount} from "svelte";
    import AiScreen from "./AiScreen.svelte";
    import ClickGuiScreen from "./ClickGuiScreen.svelte";

    function currentRoute() {
        return location.hash.replace(/^#\/?/, "").split(/[/?]/, 1)[0] || "ai";
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
{:else}
    <AiScreen />
{/if}
