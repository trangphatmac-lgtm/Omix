#!/usr/bin/env python3
"""Exercise WebUI navigation in an isolated running client (never use a normal play instance).

Usage: python3 tools/run_webui_smoke.py --game-dir /path/to/isolated/game
Uses the client's development API to exercise actual Screen/command callbacks.
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
            result = json.load(response)
        if result.get('ok') is False:
            raise RuntimeError(result)
        return result

    agent = request('/v2/sessions', {})['agentId']

    def call(name, arguments):
        return request('/v2/calls', {'id': str(uuid.uuid4()), 'agentId': agent,
            'worldEpoch': request('/v2/snapshot')['worldEpoch'], 'name': name, 'arguments': arguments})['value']

    def evaluate(source):
        prefix = 'import im.webui.WebUiRuntime;\nimport im.webui.screen.*;\nimport net.minecraft.client.gui.screen.*;\nimport net.minecraft.client.input.KeyInput;\n'
        job = call('script_evaluate', {'source': prefix + source})
        deadline = time.monotonic() + 300
        while job['state'] in ('queued', 'compiling', 'applying'):
            if time.monotonic() > deadline:
                raise TimeoutError(job)
            time.sleep(.2)
            job = call('script_job', {'jobId': job['id']})
        assert job['state'] == 'evaluated', job
        return job.get('value')

    try:
        deadline = time.monotonic() + 180
        while evaluate('return mc.getOverlay() == null && WebUiRuntime.getInstance().getState() == im.webui.WebUiState.READY;') != 'true':
            if time.monotonic() > deadline:
                raise TimeoutError('Client startup/loading overlay did not finish')
            time.sleep(1)
        evaluate('mc.setScreen(null); new cn.omix.command.impl.ScriptCommand().execute(new String[]{".script", "open"}); mc.setScreen(null); return "chat submission returned";')
        assert evaluate('return mc.currentScreen instanceof WebUiScreen web && web.getType().equals(WebScreenType.SCRIPTS);') == 'true'
        print('PASS queued .script open survives ChatScreen post-submit closure', flush=True)
        assert evaluate('mc.currentScreen.keyPressed(new KeyInput(256, 0, 0)); return mc.world == null ? mc.currentScreen instanceof cn.omix.ui.screen.impl.MainMenu : mc.currentScreen == null;') == 'true'
        print('PASS Script Studio ESC returns to game/title screen', flush=True)
        assert evaluate('var parent = new cn.omix.ui.screen.impl.MainMenu(); mc.setScreen(parent); var web = WebUiRuntime.getInstance(); web.openScreen(WebScreenType.CLICK_GUI); web.openScreen(WebScreenType.SCRIPTS); mc.currentScreen.keyPressed(new KeyInput(256, 0, 0)); return mc.currentScreen == parent;') == 'true'
        print('PASS nested ClickGUI → Scripts ESC returns to original screen', flush=True)
        # Wait for real CEF rather than accepting only the Java loading placeholder.
        deadline = time.monotonic() + 180
        evaluate('WebUiRuntime.getInstance().openScreen(WebScreenType.CLICK_GUI); return "opening";')
        while evaluate('return WebUiRuntime.getInstance().isBrowserTextureReady();') != 'true':
            if time.monotonic() > deadline:
                raise TimeoutError('CEF did not produce a visible browser texture')
            time.sleep(1)
        evaluate('var web = WebUiRuntime.getInstance(); mc.currentScreen.close(); web.openScreen(WebScreenType.SCRIPTS); return "old animation pending";')
        time.sleep(.5)
        assert evaluate('return mc.currentScreen instanceof WebUiScreen screen && screen.getType().equals(WebScreenType.SCRIPTS) && WebUiRuntime.getInstance().isBrowserTextureReady();') == 'true'
        print('PASS delayed old ClickGUI close cannot hide new Scripts browser', flush=True)
        assert evaluate('mc.currentScreen.keyPressed(new KeyInput(256, 0, 0)); return !(mc.currentScreen instanceof WebUiScreen) && !WebUiRuntime.getInstance().isBrowserTextureReady();') == 'true'
        print('PASS closing the visible panel also hides its browser', flush=True)
    finally:
        try:
            evaluate('if (mc.currentScreen instanceof WebUiScreen) mc.currentScreen.close(); return "closed";')
        finally:
            request('/v2/agents/' + agent + '/release', {})


if __name__ == '__main__':
    main()
