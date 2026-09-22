// Compatibility adapter for the pinned Harness 0.1.6-alpha.1 controller.
// It keeps separate maps for creation/adoption and history-triggered resume.
// Both paths can acquire the same persistence writer before an Agent is published.
export const name = 'omix-session-activation';
export const inject = ['sessionController'];

export function serializeActivation(controller) {
  if (typeof controller?.resolve !== 'function' || typeof controller?.ensureSession !== 'function')
    throw new Error('Unsupported Harness session activation API; review the pinned-version adapter');
  const pending = new Map();
  let active = true;
  const originals = new Map();
  const wrappers = new Map();
  for (const name of ['resolve', 'ensureSession']) {
    const original = controller[name];
    originals.set(name, original);
    const wrapper = function (id, ...args) {
      // Serialize only activation, never a model turn or different sessions.
      const result = (pending.get(id) ?? Promise.resolve()).then(() => {
        if (!active) throw new Error('Omix session activation adapter was disposed');
        return original.call(this, id, ...args);
      });
      const settled = result.then(() => {}, () => {});
      pending.set(id, settled);
      void settled.then(() => { if (pending.get(id) === settled) pending.delete(id); });
      return result;
    };
    wrappers.set(name, wrapper);
    controller[name] = wrapper;
  }
  return async () => {
    active = false;
    for (const [name, original] of originals) {
      if (controller[name] === wrappers.get(name)) controller[name] = original;
    }
    await Promise.all([...pending.values()]);
  };
}

export function apply(ctx) {
  ctx.effect(() => serializeActivation(ctx.sessionController.agents));
}
