#!/usr/bin/env python3
"""Run script lifecycle/mapping smoke tests against an isolated, already running Omix client.

Usage: python3 tools/run_script_smoke.py --game-dir /path/to/isolated/game
The test creates then removes one uniquely named script and restores host module settings.
Use an isolated test instance: script callbacks run in the actual game process.
"""
import argparse
import json
import time
import urllib.request
import uuid
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game-dir', required=True, type=Path)
    args = parser.parse_args()
    connection = json.loads((args.game_dir / 'Omix/development/bridge.json').read_text())

    def request(path, body=None):
        req = urllib.request.Request(connection['endpoint'] + path,
            data=None if body is None else json.dumps(body).encode(),
            headers={'Authorization': 'Bearer ' + connection['token'], 'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=40) as response:
            value = json.load(response)
        if value.get('ok') is False:
            raise RuntimeError(value.get('error'))
        return value

    agent = request('/v2/sessions', {})['agentId']
    script_id = 'Smoke' + uuid.uuid4().hex[:8]
    saved = None
    host_state = None

    def call(name, arguments=None):
        epoch = request('/v2/snapshot')['worldEpoch']
        return request('/v2/calls', {'id': str(uuid.uuid4()), 'agentId': agent, 'worldEpoch': epoch,
            'name': name, 'arguments': arguments or {}})['value']

    def wait(job):
        deadline = time.monotonic() + 300
        while job['state'] in ('queued', 'compiling', 'applying'):
            if time.monotonic() > deadline:
                raise TimeoutError(job)
            time.sleep(.25)
            job = call('script_job', {'jobId': job['id']})
        return job

    def evaluate(source):
        result = wait(call('script_evaluate', {'source': source}))
        assert result['state'] == 'evaluated', result
        return result.get('value')

    def write(source):
        nonlocal saved
        saved = call('script_write', {'id': script_id, 'source': source, 'expectedHash': saved['hash'] if saved else ''})

    def action(kind):
        return wait(call('script_action', {'id': script_id, 'action': kind}))

    def status():
        return next(item for item in call('script_status')['scripts'] if item['id'] == script_id)

    try:
        host_state = evaluate('var state = new com.google.gson.JsonObject(); for (String id : java.util.List.of("Speed", "Teams", "NoFog", "PathFinder")) state.add(id, cn.omix.util.script.ScriptState.capture(modules.get(id))); return state.toString();')
        source = '''void onLoad() {
    var one = modules.register("one", "Smoke One", Category.Player);
    var value = one.setting(new NumberValue("Value", 3, 0, 10));
    one.onEnable(() -> log("enabled value=" + value.getValue()));
    modules.register("two", "Smoke Two", Category.Render);
    commands.register("smokecmd", args -> log("command worked"), "smokecmd");
    modes.register("speed", "Speed", "Smoke Mode");
    modes.register("team", "Teams", "Smoke Mode").hook(ModeHooks.IS_TEAM, entity -> true);
    modes.register("fog", "NoFog", "Smoke Mode").hook(ModeHooks.INTERCEPT, operation -> operation.equals("fog"));
    modes.register("path", "PathFinder", "Smoke Mode").hook(ModeHooks.COMPUTE_PATH, query -> new ModeHooks.PathResult(java.util.List.of(query.from(), query.to())));
    script.afterCommit(() -> one.enabled(true));
}
'''
        write(source)
        job = action('load')
        assert job['state'] == 'loaded', job
        generation = job['generation']
        module = f'modules.get("script:{script_id}/one")'
        assert evaluate(f'var m = {module}; m.setKey(82); ((NumberValue)m.getValues().getFirst()).setValue(7); commands.run(".smokecmd"); return m.isEnabled();') == 'true'
        native = evaluate('class Point extends BlockPos { Point() { super(7, 8, 9); } } java.util.function.Supplier<Integer> f = () -> new Point().getX(); var name = nativeAccess.method("net.minecraft.util.math.Vec3i", "getX", "()I"); return f.get() + ":" + new Point().getClass().getMethod(name).invoke(new Point()) + ":" + cn.omix.util.misc.TimerSpeedUtil.getTimerSpeed();')
        assert native.startswith('7:7:'), native
        print('PASS native Minecraft inheritance, reflection names, lambda, inner class, client util', flush=True)
        mode = evaluate('for (String id : java.util.List.of("Speed", "Teams", "NoFog", "PathFinder")) { var m = modules.get(id); cn.omix.util.script.ModeHost.select(m, "Smoke Mode"); m.setEnabled(true); if (m.isNativeBehaviorActive()) throw new AssertionError(id); } return client.getModuleManager().getModule(cn.omix.module.impl.player.Teams.class).isTeam(null) + ":" + cn.omix.module.impl.render.NoFog.isActive() + ":" + cn.omix.util.player.pathfinder.MainPathFinder.computePath(Vec3d.ZERO, new Vec3d(1,2,3)).size();')
        assert mode == 'true:true:2', mode
        assert evaluate('cn.omix.util.script.ModeHost.select(modules.get("NoFog"), "Built-in"); var nativeActive = modules.get("NoFog").isNativeBehaviorActive(); cn.omix.util.script.ModeHost.select(modules.get("NoFog"), "Smoke Mode"); return nativeActive + ":" + !modules.get("NoFog").isNativeBehaviorActive();') == 'true:true'
        print('PASS primary mode, synthetic Behavior, bidirectional switch, typed direct hooks, permanent provider', flush=True)
        write(source + '\n// replacement')
        job = action('reload')
        assert job['state'] == 'loaded' and job['generation'] != generation, job
        generation = job['generation']
        preserved = evaluate(f'var m = {module}; return m.isEnabled()+":"+m.getKey()+":"+((NumberValue)m.getValues().getFirst()).getValue()+":"+modules.list().stream().filter(v -> v.getId().startsWith("script:{script_id}/")).count()+":"+cn.omix.util.script.ModeHost.selected(modules.get("Speed"));')
        assert preserved == 'true:82:7.0:2:Smoke Mode', preserved
        print('PASS multi-module replacement preserves key, setting, enabled state and selected host mode', flush=True)
        for label, invalid in [('compile', 'void onLoad() { missing(); }'),
                ('registration', 'void onLoad() { modules.register("one", "Aura", Category.Combat); }'),
                ('activation', source.replace('log("enabled value=" + value.getValue())', '{ throw new AssertionError("activation regression"); }'))]:
            write(invalid)
            failed = action('reload')
            assert failed['state'] == 'failed', failed
            assert status()['generation'] == generation
            assert evaluate(f'var m={module}; return m.isEnabled()+":"+((NumberValue)m.getValues().getFirst()).getValue();') == 'true:7.0'
            print('PASS ' + label + ' failure retains/restores previous generation', flush=True)
        log = call('script_logs', {'id': script_id, 'limit': 100})
        assert any(entry['line'] > 0 and 'activation regression' in entry['message'] for entry in log['entries']), log
        call('script_action', {'id': script_id, 'action': 'unload'})
        assert not status()['loaded']
        assert evaluate('return modules.get("Speed").isEnabled()+":"+modules.get("NoFog").isEnabled()+":"+modules.get("PathFinder").isNativeBehaviorActive()+":"+modules.get("NoFog").getValues().stream().anyMatch(v -> v.getName().equals("Behavior"));') == 'false:false:true:false'
        print('PASS selected mode unload removes Behavior, disables switchable hosts and restores permanent provider', flush=True)
        screenshot = call('script_screenshot')
        assert screenshot['mimeType'] == 'image/png' and len(screenshot['data']) > 100
        print('PASS native framebuffer screenshot:', screenshot['path'], flush=True)
        print('LIVE SCRIPT SMOKE PASSED', flush=True)
    finally:
        try:
            call('script_action', {'id': script_id, 'action': 'unload'})
            if saved:
                call('script_delete', {'id': script_id, 'expectedHash': saved['hash']})
            if host_state:
                evaluate('var state = com.google.gson.JsonParser.parseString(' + json.dumps(host_state) + ').getAsJsonObject(); for(var entry : state.entrySet()) cn.omix.util.script.ScriptState.restore(modules.get(entry.getKey()), entry.getValue().getAsJsonObject()); return "restored";')
        finally:
            request('/v2/agents/' + agent + '/release', {})


if __name__ == '__main__':
    main()
