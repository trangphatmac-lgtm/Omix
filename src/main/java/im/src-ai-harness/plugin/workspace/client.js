// Harness client-plugin registration format; no changes to upstream bundles.
window.__ModuleLoader__.load({
  id: 'omix-harness-workspace',
  factory: () => ({
    inject: ['workspaces', 'sessions', 'uiWorkspace'],
    apply(ctx) {
      const workspaceId = window.__OMIX_WORKSPACE_ID__;
      if (typeof workspaceId !== 'string') throw new Error('Omix default Workspace is missing');
      ctx.effect(() => {
        let selected = false;
        const reconcile = () => {
          if (selected) return;
          const workspaces = ctx.workspaces.list.getSnapshot();
          const sessions = ctx.sessions.list.getSnapshot();
          if (workspaces.phase !== 'ready' || sessions.phase !== 'ready') return;
          const workspace = workspaces.items.find(item => item.workspaceId === workspaceId);
          if (!workspace) return;
          selected = true;
          // Preserve a restored conversation already in the default workspace.
          if (sessions.current && workspace.sessionIds.includes(sessions.current)) return;
          void ctx.uiWorkspace.openWorkspace(workspaceId).catch(error => {
            console.error('Omix default Workspace selection failed:', error);
          });
        };
        const stopWorkspaces = ctx.workspaces.list.subscribe(reconcile);
        const stopSessions = ctx.sessions.list.subscribe(reconcile);
        reconcile();
        return () => { selected = true; stopWorkspaces(); stopSessions(); };
      });
    },
  }),
});
