import {mount} from "svelte";
import App from "./App.svelte";
import "./style.css";

const target = document.querySelector<HTMLDivElement>("#app");

if (!target) {
    throw new Error("Missing WebUI mount point.");
}

mount(App, {target});
