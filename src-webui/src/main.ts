import "./style.css";

type CheckState = "pending" | "ok" | "failed";

const checks = new Map<string, CheckState>([
    ["HTTP 静态资源", "ok"],
    ["REST 客户端信息", "pending"],
    ["Screen acknowledgement", "pending"],
    ["WebSocket 连接", "pending"],
    ["WebSocket 往返", "pending"],
    ["REST 往返", "pending"],
    ["LocalStorage 持久层", "pending"],
    ["CEF 输入桥", "pending"]
]);
let baseReported = false;
let finalReported = false;

const app = document.querySelector<HTMLDivElement>("#app")!;
app.innerHTML = `
  <main class="card">
    <div class="eyebrow">REMIX × MCEF</div>
    <h1>WebUI 完整循环测试</h1>
    <p id="summary">正在连接 Minecraft 客户端…</p>
    <div id="checks" class="checks"></div>
    <label>
      输入桥测试
      <input id="input-test" autofocus autocomplete="off" placeholder="在这里输入中文 / English"/>
    </label>
    <pre id="details"></pre>
  </main>
`;

const checksNode = document.querySelector<HTMLDivElement>("#checks")!;
const summaryNode = document.querySelector<HTMLParagraphElement>("#summary")!;
const detailsNode = document.querySelector<HTMLPreElement>("#details")!;
const inputNode = document.querySelector<HTMLInputElement>("#input-test")!;

function renderChecks() {
    checksNode.innerHTML = [...checks].map(([name, state]) =>
        `<div class="check ${state}"><span></span>${name}</div>`
    ).join("");

    const values = [...checks.values()];
    if (values.includes("failed")) {
        summaryNode.textContent = "循环测试存在失败项，请查看日志。";
        document.body.dataset.state = "failed";
    } else if (values.every(value => value === "ok")) {
        summaryNode.textContent = "浏览器、REST、WebSocket、Screen 和输入框架均已接通。";
        document.body.dataset.state = "ok";
        if (!finalReported) {
            finalReported = true;
            void post("/api/v1/client/test/report", {
                step: "complete",
                status: "passed",
                checks: Object.fromEntries(checks)
            });
        }
    }

    const baseChecks = [...checks].filter(([name]) => name !== "CEF 输入桥");
    if (!baseReported && baseChecks.every(([, state]) => state === "ok")) {
        baseReported = true;
        const bounds = inputNode.getBoundingClientRect();
        void post("/api/v1/client/test/report", {
            step: "base-complete",
            status: "passed",
            x: bounds.left + bounds.width / 2,
            y: bounds.top + bounds.height / 2
        });
    }
}

inputNode.addEventListener("input", () => {
    void post("/api/v1/client/test/report", {
        step: "input-observed",
        status: "received",
        value: inputNode.value
    });
    if (inputNode.value.includes("Remix")) {
        setCheck("CEF 输入桥", "ok");
        appendDetail({inputBridge: inputNode.value});
    }
});

function setCheck(name: string, state: CheckState) {
    checks.set(name, state);
    renderChecks();
}

function appendDetail(value: unknown) {
    detailsNode.textContent += `${typeof value === "string" ? value : JSON.stringify(value, null, 2)}\n`;
}

async function post(path: string, body: unknown = {}) {
    return fetch(path, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body)
    });
}

async function request(method: string, path: string, body?: unknown) {
    return fetch(path, {
        method,
        headers: body === undefined ? undefined : {"Content-Type": "application/json"},
        body: body === undefined ? undefined : JSON.stringify(body)
    });
}

async function acknowledgeScreen() {
    for (let attempt = 0; attempt < 40; attempt++) {
        try {
            const response = await post("/api/v1/client/virtualScreen", {name: "test"});
            if (response.ok) {
                setCheck("Screen acknowledgement", "ok");
                return;
            }
        } catch (error) {
            appendDetail(String(error));
        }
        await new Promise(resolve => setTimeout(resolve, 250));
    }
    setCheck("Screen acknowledgement", "failed");
}

async function run() {
    renderChecks();

    try {
        const infoResponse = await fetch("/api/v1/client/info");
        const info = await infoResponse.json();
        appendDetail(info);
        setCheck("REST 客户端信息", infoResponse.ok ? "ok" : "failed");
    } catch (error) {
        appendDetail(String(error));
        setCheck("REST 客户端信息", "failed");
    }

    void acknowledgeScreen();

    try {
        const restPing = await post("/api/v1/client/test/ping", {value: "rest-ping"});
        const result = await restPing.json();
        appendDetail(result);
        setCheck("REST 往返", result.value === "rest-pong" ? "ok" : "failed");
    } catch (error) {
        appendDetail(String(error));
        setCheck("REST 往返", "failed");
    }

    try {
        const key = "framework.test";
        const storedValue = {value: "persisted", timestamp: 1};
        const write = await request("PUT", "/api/v1/client/localStorage", {key, value: storedValue});
        const read = await fetch(`/api/v1/client/localStorage?key=${encodeURIComponent(key)}`);
        const readBody = await read.json();
        const remove = await request(
            "DELETE",
            `/api/v1/client/localStorage?key=${encodeURIComponent(key)}`
        );
        const ok = write.ok
            && read.ok
            && readBody.value?.value === storedValue.value
            && remove.ok;
        setCheck("LocalStorage 持久层", ok ? "ok" : "failed");
    } catch (error) {
        appendDetail(String(error));
        setCheck("LocalStorage 持久层", "failed");
    }

    const scheme = location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${scheme}://${location.host}/ws`);

    socket.addEventListener("open", () => {
        setCheck("WebSocket 连接", "ok");
        socket.send(JSON.stringify({name: "testPing", event: {value: "ws-ping"}}));
    });
    socket.addEventListener("message", event => {
        const packet = JSON.parse(event.data);
        appendDetail(packet);
        if (packet.name === "testPong" && packet.event?.value === "ws-pong") {
            setCheck("WebSocket 往返", "ok");
        }
    });
    socket.addEventListener("error", () => {
        setCheck("WebSocket 连接", "failed");
        setCheck("WebSocket 往返", "failed");
    });
}

void run();
