export interface ClickGuiNavigation {
    view: "modules" | "configs";
    activeCategory: string;
    selectedModuleName: string;
    search: string;
    selectedConfig: string;
    moduleScroll: number;
    settingsScroll: number;
    configScroll: number;
}

const STORAGE_KEY = "clickgui.navigation.v1";
let cached: ClickGuiNavigation | undefined;
let writes: Promise<void> = Promise.resolve();

export function normalizeNavigation(value: unknown): ClickGuiNavigation {
    const data = value && typeof value === "object"
        ? value as Record<string, unknown> : {};
    const text = (key: string, fallback = "") =>
        typeof data[key] === "string" ? data[key] as string : fallback;
    const scroll = (key: string) => typeof data[key] === "number"
        && Number.isFinite(data[key]) ? Math.max(0, data[key] as number) : 0;
    return {
        view: data.view === "configs" ? "configs" : "modules",
        activeCategory: text("activeCategory", "Combat"),
        selectedModuleName: text("selectedModuleName"),
        search: text("search"),
        selectedConfig: text("selectedConfig"),
        moduleScroll: scroll("moduleScroll"),
        settingsScroll: scroll("settingsScroll"),
        configScroll: scroll("configScroll")
    };
}

export async function loadNavigation(): Promise<ClickGuiNavigation> {
    if (cached) return {...cached};
    let value: unknown;
    try {
        const response = await fetch(`/api/v1/client/localStorage?key=${encodeURIComponent(STORAGE_KEY)}`);
        if (!response.ok) throw new Error("No saved navigation");
        value = (await response.json()).value;
    } catch {
        try {
            value = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null");
        } catch {
            // Storage may be unavailable, or a previous value may be corrupt.
        }
    }
    cached ??= normalizeNavigation(value);
    return {...cached};
}

export function saveNavigation(value: ClickGuiNavigation): Promise<void> {
    cached = normalizeNavigation(value);
    const snapshot = JSON.stringify(cached);
    try {
        localStorage.setItem(STORAGE_KEY, snapshot);
    } catch {
        // The Java bridge also persists across changes to the local server port.
    }
    // Serialize writes so a slow earlier request cannot overwrite the last page.
    writes = writes.then(async () => {
        try {
            await fetch("/api/v1/client/localStorage", {
                method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({key: STORAGE_KEY, value: JSON.parse(snapshot)}),
                keepalive: true
            });
        } catch {
            // Keep the in-memory/browser copy when the bridge is unavailable.
        }
    });
    return writes;
}
