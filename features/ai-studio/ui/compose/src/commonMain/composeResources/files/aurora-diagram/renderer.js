const maxPixels = 4_000_000;
const maxAxis = 4096;
const pngPrefix = "data:image/png;base64,";
const loadScript = (file) => new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = file;
    script.onload = resolve;
    script.onerror = () => reject(new Error("Diagram engine is unavailable"));
    document.head.appendChild(script);
});

let mermaidEngine;
async function renderMermaid(source) {
    mermaidEngine ??= loadScript("mermaid.min.js").then(() => {
        window.mermaid.initialize({
            startOnLoad: false,
            securityLevel: "strict",
            htmlLabels: false,
            suppressErrorRendering: true,
            maxTextSize: 32_768,
            maxEdges: 1000,
            secure: [
                "secure", "securityLevel", "startOnLoad", "suppressErrorRendering",
                "htmlLabels", "maxTextSize", "maxEdges",
            ],
        });
        return window.mermaid;
    });
    const mermaid = await mermaidEngine;
    const { svg } = await mermaid.render("aurora-diagram", source);
    return svg;
}

let plantumlEngine;
async function renderPlantUml(source) {
    const starts = source.match(/^\s*@start\w+\b/gim) ?? [];
    if (starts.length > 1) throw new Error("Expected one PlantUML diagram");
    const standalone = starts.length ? source : `@startuml\n${source}\n@enduml`;
    // Local and remote user-supplied files never enter the renderer. Bundled
    // standard-library lookups use the renderer's own origin.
    window.PLANTUML_STDLIB_BASE = new URL("./", location.href).href;
    plantumlEngine ??= loadScript("viz-global.js").then(() => import("./plantuml.js"));
    const { renderToString } = await plantumlEngine;
    return new Promise((resolve, reject) => {
        renderToString(standalone.split(/\r\n|\r|\n/), resolve, (error) => reject(new Error(String(error))));
    });
}

async function rasterize(markup, format) {
    const document = new DOMParser().parseFromString(markup, "image/svg+xml");
    const svg = document.documentElement;
    if (svg.localName !== "svg") throw new Error("Diagram is not an SVG image");
    // PlantUML's browser API can return an error as an SVG. Its first group in
    // that case starts with the engine's green diagnostic version banner.
    const firstLabel = svg.querySelector("svg > g > text:first-child");
    if (format === "plantuml" && firstLabel?.getAttribute("fill") === "#33FF02" &&
        firstLabel.textContent.startsWith("PlantUML version ")) {
        throw new Error("PlantUML rejected the diagram");
    }
    const viewBox = svg.viewBox.baseVal;
    const width = viewBox.width || svg.width.baseVal.value;
    const height = viewBox.height || svg.height.baseVal.value;
    if (!(width > 0 && height > 0 && Number.isFinite(width) && Number.isFinite(height))) {
        throw new Error("Diagram has invalid dimensions");
    }
    const scale = Math.min(2, maxAxis / width, maxAxis / height, Math.sqrt(maxPixels / (width * height)));
    svg.setAttribute("width", Math.max(1, Math.floor(width * scale)));
    svg.setAttribute("height", Math.max(1, Math.floor(height * scale)));
    if (!svg.hasAttribute("viewBox")) svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
    const url = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svg)], { type: "image/svg+xml" }));
    try {
        const image = new Image();
        await new Promise((resolve, reject) => {
            image.onload = resolve;
            image.onerror = () => reject(new Error("Diagram image could not be decoded"));
            image.src = url;
        });
        const canvas = window.document.createElement("canvas");
        canvas.width = image.naturalWidth;
        canvas.height = image.naturalHeight;
        canvas.getContext("2d").drawImage(image, 0, 0);
        const png = canvas.toDataURL("image/png");
        if (!png.startsWith(pngPrefix) || png.length > 3_000_000) throw new Error("Diagram image is too large");
        return png.slice(pngPrefix.length);
    } finally {
        URL.revokeObjectURL(url);
    }
}

let result;
window.takeAuroraDiagramChunk = (offset, length) => result?.substring(offset, offset + length) ?? null;
window.startAuroraDiagram = async (format, encodedSource) => {
    result = null;
    try {
        const source = new TextDecoder().decode(Uint8Array.from(atob(encodedSource), (character) => character.charCodeAt(0)));
        const svg = await (format === "mermaid" ? renderMermaid(source) : renderPlantUml(source));
        result = `ok:${await rasterize(svg, format)}`;
    } catch (error) {
        result = "error";
    }
    window.auroraDiagramResult?.({ request: result });
};
