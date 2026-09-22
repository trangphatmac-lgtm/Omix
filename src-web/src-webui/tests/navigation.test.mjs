import {test} from "node:test";
import assert from "node:assert/strict";

let instance = 0;
const freshModule = () => import(`../src/clickGuiNavigation.ts?test=${instance++}`);

test("malformed navigation falls back without losing valid fields", async () => {
    const {normalizeNavigation} = await freshModule();
    const defaults = normalizeNavigation(null);
    assert.equal(defaults.view, "modules");
    assert.equal(defaults.activeCategory, "Combat");
    assert.deepEqual(normalizeNavigation({view: "unknown", activeCategory: 3,
        moduleScroll: -2, settingsScroll: Infinity, configScroll: "8"}), defaults);
    assert.equal(normalizeNavigation({view: "configs", search: "Aura"}).search, "Aura");
});

test("bridge restores the last page even with empty browser storage", async (t) => {
    const {loadNavigation, normalizeNavigation} = await freshModule();
    const saved = normalizeNavigation({view: "configs", activeCategory: "Move",
        selectedModuleName: "Speed", selectedConfig: "PVP", settingsScroll: 123});
    t.mock.method(globalThis, "fetch", async () => Response.json({value: saved}));
    assert.deepEqual(await loadNavigation(), saved);
    const copy = await loadNavigation();
    copy.view = "modules";
    assert.equal((await loadNavigation()).view, "configs");
});

test("unavailable bridge and corrupt browser storage do not block opening", async (t) => {
    const {loadNavigation, normalizeNavigation} = await freshModule();
    t.mock.method(globalThis, "fetch", async () => {throw new Error("offline");});
    const previous = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
    Object.defineProperty(globalThis, "localStorage", {configurable: true,
        value: {getItem: () => "{broken"}});
    t.after(() => previous ? Object.defineProperty(globalThis, "localStorage", previous)
        : delete globalThis.localStorage);
    assert.deepEqual(await loadNavigation(), normalizeNavigation(null));
});

test("rapid saves retain the newest page and serialize bridge writes", async (t) => {
    const {loadNavigation, saveNavigation, normalizeNavigation} = await freshModule();
    let release;
    const pending = new Promise(resolve => release = resolve);
    const requests = [];
    t.mock.method(globalThis, "fetch", async (_url, options) => {
        requests.push(JSON.parse(options.body).value);
        if (requests.length === 1) await pending;
        return new Response(null, {status: 204});
    });
    const first = normalizeNavigation({activeCategory: "Move", selectedModuleName: "Speed"});
    const last = {...first, view: "configs", selectedConfig: "PVP", configScroll: 80};
    const firstWrite = saveNavigation(first);
    const lastWrite = saveNavigation(last);
    assert.deepEqual(await loadNavigation(), last);
    assert.equal(requests.length, 1);
    release();
    await Promise.all([firstWrite, lastWrite]);
    assert.deepEqual(requests, [first, last]);
});
