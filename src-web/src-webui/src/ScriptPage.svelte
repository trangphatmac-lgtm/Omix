<script lang="ts">
    import {onMount} from 'svelte';
    let url = '', error = '';
    onMount(() => {
        void fetch('/api/v1/client/virtualScreen', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({name:'script-page'})});
        void fetch('/api/v1/scripts/activePage').then(r => r.json()).then(page => {
            if (!page.id) { error = '此脚本页面已经卸载。'; return; }
            url = `/api/v1/scripts/page?id=${encodeURIComponent(page.id)}&generation=${page.generation}`;
        }).catch(e => error = String(e));
    });
</script>
{#if url}<iframe title="脚本页面" src={url}></iframe>{:else}<p>{error || '正在打开脚本页面…'}</p>{/if}
<style>iframe{position:fixed;inset:0;width:100%;height:100%;border:0;background:#111827}p{padding:30px;color:white}</style>
