# Remix WebUI framework

This package ports the architecture of LiquidBounce's Web-based in-game GUI to
Remix. It deliberately contains framework primitives and one diagnostic page,
not a production interface.

## Source correspondence

| LiquidBounce concept | Remix implementation | Responsibility |
| --- | --- | --- |
| `integration/backend/BrowserBackend` | `im.webui.backend.BrowserBackend` | Browser engine lifecycle and browser factory |
| `integration/backend/backends/cef` | `im.webui.backend.cef` | MCEF/JCEF initialization and browser adapter |
| `integration/backend/browser/Browser` | `im.webui.backend.Browser` | URL, viewport, texture, navigation and input contract |
| `BrowserViewport` / `BrowserSettings` | Same names under `im.webui.backend` | Full-frame or bounded viewport, quality, FPS and acceleration |
| `InputAcceptor` / browser input handling | `im.webui.backend.input` | Priority-ordered input dispatch |
| browser renderer | `im.webui.render.BrowserRenderer` | Draw CEF textures in Minecraft GUI space |
| `ScreenManager` and Web screen types | `im.webui.screen.WebScreenManager` / `WebScreenType` | Route registration, opening and acknowledgement |
| shared/standalone/overlay screens | `WebUiScreen`, `StandaloneWebUiScreen`, `WebOverlay` | Reusable browser ownership models |
| theme manager | `im.webui.theme` | Resolve logical screen types to authenticated hash routes |
| client interop server | `im.webui.interop.InteropServer` | Loopback HTTP assets, REST routes and WebSocket events |
| route/event registries | `InteropRouteRegistry` / `WebSocketEventBus` | Add APIs without editing the Netty transport |
| local storage API | `PersistentLocalStorage` | Atomic JSON-backed client persistence |

The port follows the upstream separation of concerns rather than copying
client-specific APIs such as modules, accounts, servers or combat state. Those
are application features to add later through the route and event registries.

## Lifecycle

1. `Client.onInitializeClient` starts `WebUiRuntime`.
2. The loopback server binds to `127.0.0.1` on an ephemeral port and generates a
   per-process authentication token.
3. MCEF checks/downloads JCEF off the render thread.
4. MCEF is initialized on the render thread and creates the shared browser.
5. The browser loads the ZIP-packaged Vite output through the local server.
6. `MixinGameRenderer` pumps the CEF message loop and browser updates each frame.
7. A `WebScreenType` resolves through `WebThemeManager` to a hash route.
8. The front end acknowledges the route through REST before the screen manager
   considers it synchronized.
9. Keyboard, character, mouse and resize mixins forward GLFW events to the
   priority-ordered browser input router.
10. Client shutdown closes browsers, Netty channels/event loops and MCEF.

## Browser ownership models

- `WebUiScreen` uses the runtime's shared full-frame browser. This is the normal
  model for switching between main GUI routes without recreating Chromium.
- `StandaloneWebUiScreen` owns and closes a dedicated browser. Use it for an
  isolated page or different FPS/quality requirements.
- `WebOverlay` lazily owns a dedicated browser while rendering over normal game
  content. Its `InputAcceptor` decides whether it currently participates in
  input routing.

Browsers have a signed short priority. Rendering is low-to-high priority; input
is evaluated in reverse order so the topmost accepting browser wins.

## Transport and security

- HTTP and WebSocket share the same random loopback port.
- The initial URL carries a cryptographically random `remix_code`.
- A successful authenticated request installs an HttpOnly, SameSite=Strict
  cookie; APIs and the WebSocket upgrade reject unauthenticated requests.
- Static files are served directly from `assets/remix/webui/webui.zip`.
- SPA fallback returns `index.html` for extensionless paths.
- CORS/preflight support permits a separately hosted development front end while
  retaining token authentication.
- REST handlers are registered by method/path in `InteropRouteRegistry`.
- WebSocket handlers are registered by event name in `WebSocketEventBus`.

## Front-end build

`src-webui` is a minimal TypeScript/Vite project. Gradle owns its toolchain:

- fixed Node version: 22.14.0;
- `npmInstall` restores dependencies from `package-lock.json`;
- `buildWebUi` runs the Vite production build;
- `bundleWebUi` creates `webui.zip`;
- `processResources` embeds the ZIP in the mod JAR.

`node_modules` and `dist` are generated and intentionally ignored.

## Diagnostic page

The only bundled page is `#/test`. It is a framework acceptance test, not a UI
prototype. With `-Dremix.webui.autoTest=true` (currently the default during this
migration phase), it validates:

- ZIP-served static assets;
- authenticated REST client information;
- screen acknowledgement and route synchronization;
- WebSocket connection and request/reply;
- REST request/reply;
- JSON local-storage write/read/delete persistence;
- CEF mouse focus and character input.

The Java runtime logs `[WebUI Test] complete = passed` only after every check
reports `ok`.

## Extension points for future interfaces

1. Register logical routes with `WebScreenType.register`.
2. Add the matching hash routes to the front-end router.
3. Open a shared route through `WebUiRuntime.openScreen`.
4. Register domain REST endpoints through
   `WebUiRuntime.getInteropServer().getRoutes()`.
5. Register push/request events through
   `WebUiRuntime.getInteropServer().getSocketEvents()`.
6. Keep client-domain serialization outside the transport classes.
7. Turn `remix.webui.autoTest` off by default when the first real interface
   replaces the diagnostic page.

No module list, settings editor, HUD designer, account view or other production
screen is included in this migration.
