import { mkdir } from 'node:fs/promises';
import { isAbsolute } from 'node:path';

export const name = 'omix-workspace';
export const inject = ['workspaceRegistry'];

export async function apply(ctx) {
  const path = process.env.OMIX_AI_WORKSPACE;
  if (!path || !isAbsolute(path)) throw new Error('OMIX_AI_WORKSPACE must be an absolute game Workspace path');
  await mkdir(path, { recursive: true });
  const workspace = await ctx.workspaceRegistry.create(path, 'Workspace');
  ctx.on('webserver/index-inject', rows => rows.push({
    kind: 'global', name: '__OMIX_WORKSPACE_ID__', value: workspace.id,
  }));
}
