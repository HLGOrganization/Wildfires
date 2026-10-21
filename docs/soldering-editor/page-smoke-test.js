// Headless smoke test for the soldering page script. It stubs just enough of AUI's DOM and the
// WildfiresSoldering KubeJS binding to exercise parsing, hit-testing and the action calls. It does
// not render pixels; drawBoard's canvas calls are recorded as no-ops.
const fs = require("fs");

const pagePath = process.argv[2];
let page = fs.readFileSync(pagePath, "utf8");
if (pagePath.toLowerCase().endsWith(".html")) {
    const match = page.match(/<script>([\s\S]*)<\/script>/);
    if (!match) throw new Error("no <script> block in " + pagePath);
    page = match[1];
}

const drawCalls = [];
const canvasContext = new Proxy({}, {
    get(target, prop) {
        if (prop === "measureText") return () => ({width: 0});
        if (prop === "createLinearGradient" || prop === "createRadialGradient") {
            return () => ({addColorStop() {}});
        }
        return (...args) => { drawCalls.push([String(prop), ...args]); };
    },
    set() { return true; }
});

function makeElement(id) {
    const listeners = {};
    const attrs = {};
    return {
        id,
        innerText: "",
        value: "",
        clientWidth: 256,
        clientHeight: 256,
        attrs,
        listeners,
        classList: {add() {}, remove() {}},
        addEventListener(type, fn) { listeners[type] = fn; },
        getAttribute(key) { return attrs[key] !== undefined ? attrs[key] : null; },
        setAttribute(key, value) { attrs[key] = String(value); },
        hasAttribute(key) { return attrs[key] !== undefined; },
        removeAttribute(key) { delete attrs[key]; },
        querySelector() { return null; },
        querySelectorAll() { return []; },
        getWidth() { return 256; },
        getHeight() { return 256; },
        getContext() { return canvasContext; }
    };
}

const elements = {};
function element(id) {
    if (!elements[id]) elements[id] = makeElement(id);
    return elements[id];
}

const paletteItems = [];
for (let type = 1; type <= 5; type++) {
    const item = makeElement("palette-" + type);
    item.setAttribute("data-type", String(type));
    const count = makeElement("count-" + type);
    item.querySelector = (selector) => selector === "[data-count]" ? count : null;
    paletteItems.push(item);
}
element("palette").querySelectorAll = (selector) => selector === ".palette-item" ? paletteItems : [];

const documentStub = {
    readyState: "loading",
    body: makeElement("body"),
    getElementById: element,
    addEventListener(type, fn) { this.listeners = this.listeners || {}; this.listeners[type] = fn; },
    querySelector() { return null; },
    querySelectorAll() { return []; }
};
global.document = documentStub;
global.window = {};
global.setInterval = () => 1;
global.clearInterval = () => {};
global.setTimeout = () => 1;

const actions = [];
const sampleCells = new Array(64).fill("0");
sampleCells[0] = "1";          // resistor (1,0)
sampleCells[18] = "2";         // capacitor (2,2)
sampleCells[36] = "3";         // switch (4,4)
sampleCells[41] = "c";         // diode vertical (1,5): 4 | 8
sampleCells[13] = "d";         // IC vertical (5,1): 5 | 8
const sampleState = "R2|C" + sampleCells.join("") + "|W2|0c08c3";

let creative = false;
global.WildfiresSoldering = {
    state: () => sampleState,
    text: (key) => key,
    stock: () => 5,
    copperWire: () => 20,
    boards: () => 2,
    creative: () => creative,
    wireCost: (x1, y1, x2, y2) => Math.abs(x1 - x2) + Math.abs(y1 - y2),
    place: (...args) => { actions.push(["place", ...args]); return true; },
    removeComponent: (...args) => { actions.push(["remove", ...args]); return true; },
    connect: (...args) => { actions.push(["connect", ...args]); return true; },
    disconnect: (...args) => { actions.push(["disconnect", ...args]); return true; },
    complete: () => { actions.push(["complete"]); return true; },
    clear: () => { actions.push(["clear"]); return true; }
};

// The page script is ES5 and declares everything with var/function; direct eval puts those
// declarations in this module scope so the assertions below can see them.
eval(page);

function assert(condition, message) {
    if (!condition) throw new Error("ASSERT FAILED: " + message);
}

init();

assert(cells.length === 64, "cells array length");
assert(cells[0] === 1, "resistor code decoded");
assert(cells[41] === 12, "diode vertical code decoded");
assert(wires.length === 2, "two wires decoded");
assert(wires[0].x1 === 0 && wires[0].y1 === 0 && wires[0].x2 === 3 && wires[0].y2 === 0,
    "first wire endpoints");
assert(wires[1].x1 === 3 && wires[1].y1 === 0 && wires[1].x2 === 3 && wires[1].y2 === 4,
    "second wire endpoints");

const resistor = componentAt(0, 0);
assert(resistor && resistor.type === 1 && resistor.width === 2 && resistor.height === 1,
    "componentAt resistor");
const diode = componentAt(1, 5);
assert(diode && diode.type === 4 && diode.vertical === true, "componentAt vertical diode");
assert(componentAt(7, 7) === null, "empty cell has no component");

assert(canPlaceClient(2, 6, 6, false, false) === true, "can place capacitor in empty area");
assert(canPlaceClient(2, 2, 2, false, false) === false, "cannot place over capacitor");
assert(canPlaceClient(2, 7, 7, false, false) === false, "cannot place out of bounds");
assert(canPlaceClient(2, 3, 0, false, false) === true,
    "a wire endpoint is a legal component pin");
// Capacitor at (2,2) occupies (2..3,2..3); its right neighbour is free, its own cells are not.
assert(componentAt(4, 2) === null, "cell right of the capacitor is free");
assert(canPlaceClient(1, 0, 2, false, false) === true,
    "resistor next to the capacitor fits");
assert(canPlaceClient(2, 4, 5, false, false) === true,
    "a second capacitor next to the first fits");
assert(canPlaceClient(1, 3, 2, false, false) === false,
    "resistor overlapping the capacitor is rejected");
assert(placeFailureReason(1, 3, 2, false, false).indexOf("overlap:type2@2,2") === 0,
    "overlap reason names the capacitor anchor");
assert(wireAt(nodeX(1), nodeY(0)) === 0, "wire hit test on horizontal segment");
assert(wireAt(nodeX(3), nodeY(2)) === 1, "wire hit test on vertical segment");

drawBoard();
assert(drawCalls.length > 0, "drawBoard issued canvas calls");

// Wire mode: first click sets pending, second click emits connect(0,0,4,4).
setMode("wire");
onCanvasDown({button: 0, offsetX: nodeX(6), offsetY: nodeY(6)});
assert(pending && pending.x === 6 && pending.y === 6, "pending hole set");
onCanvasDown({button: 0, offsetX: nodeX(7), offsetY: nodeY(7)});
assert(actions.some(a => a[0] === "connect" && a[1] === 6 && a[2] === 6 && a[3] === 7 && a[4] === 7),
    "connect action sent");

// Creative display: palette and copper show the infinity glyph, no empty class.
creative = true;
refresh();
assert(element("copperCount").innerText === "∞", "creative copper shows infinity");
assert(paletteItems[0].querySelector("[data-count]").innerText === "∞",
    "creative palette count shows infinity");
creative = false;
refresh();
assert(element("copperCount").innerText === "20", "survival copper shows the real count");
assert(paletteItems[0].querySelector("[data-count]").innerText === "5",
    "survival palette count shows the real count");

// R key rotates the pending component; a second R rotates it back.
selectPalette(4);
assert(mode === "place" && selectedType === 4, "palette switches to place mode");
assert(typeof document.listeners["keydown"] === "function", "keydown listener registered");
document.listeners["keydown"]({key: "r", preventDefault() {}});
assert(vertical === true, "R toggles the pending component to vertical");
document.listeners["keydown"]({key: "R", preventDefault() {}});
assert(vertical === false, "R toggles the pending component back to horizontal");
toggleFlip();
assert(flipped === true, "diode flip toggled");
onCanvasDown({button: 0, offsetX: nodeX(6), offsetY: nodeY(6)});
assert(actions.some(a => a[0] === "place" && a[1] === 4 && a[2] === 6 && a[3] === 6
    && a[4] === 0 && a[5] === 1), "place action sent with flip bit");

// Capacitor placement feedback: an overlapping click is rejected with a visible reason; the
// adjacent free 2x2 position is accepted. This is the "after placing a capacitor nothing else
// can be placed" regression check.
selectPalette(2);
actions.length = 0;
onCanvasDown({button: 0, offsetX: nodeX(3), offsetY: nodeY(2)});
assert(actions.length === 0, "overlapping capacitor placement is rejected");
assert(element("diagLine").innerText.indexOf("place rejected: overlap:type2@2,2") >= 0,
    "place rejection diagnostic contains the blocking capacitor");
actions.length = 0;
onCanvasDown({button: 0, offsetX: nodeX(4), offsetY: nodeY(5)});
assert(actions.some(a => a[0] === "place" && a[1] === 2 && a[2] === 4 && a[3] === 5),
    "a capacitor can be placed next to the first");

// Remove mode: click the capacitor at (2,2).
setMode("remove");
onCanvasDown({button: 0, offsetX: nodeX(2), offsetY: nodeY(2)});
assert(actions.some(a => a[0] === "remove" && a[1] === 2 && a[2] === 2), "remove action sent");

// Remove mode: click the horizontal wire away from a hole center.
setMode("remove");
onCanvasDown({button: 0, offsetX: nodeX(3), offsetY: nodeY(1)});
assert(actions.some(a => a[0] === "disconnect"), "disconnect action sent");

// A wire endpoint may be a component cell; (2,2) is the capacitor anchor.
setMode("wire");
actions.length = 0;
onCanvasDown({button: 0, offsetX: nodeX(2), offsetY: nodeY(2)});
assert(pending && pending.x === 2 && pending.y === 2, "component pin accepted as wire endpoint");
onCanvasDown({button: 0, offsetX: nodeX(6), offsetY: nodeY(6)});
assert(actions.some(a => a[0] === "connect" && a[1] === 2 && a[2] === 2 && a[3] === 6 && a[4] === 6),
    "wire from a component pin sent");

// Pure left click must never take the delete branch.
setMode("wire");
actions.length = 0;
onCanvasDown({button: 0, buttons: 1, offsetX: nodeX(2), offsetY: nodeY(2)});
assert(actions.length === 0, "pure left click does not delete");

// Right-click: component first, then trace, then empty hole (no action).
setMode("wire");
actions.length = 0;
onCanvasDown({button: 2, offsetX: nodeX(2), offsetY: nodeY(2), preventDefault() {}});
assert(actions.length === 1 && actions[0][0] === "remove" && actions[0][1] === 2 && actions[0][2] === 2,
    "right-click removes a component");
actions.length = 0;
onCanvasDown({button: 2, offsetX: nodeX(3), offsetY: nodeY(1), preventDefault() {}});
assert(actions.length === 1 && actions[0][0] === "disconnect", "right-click removes a trace");
actions.length = 0;
onCanvasDown({button: 2, offsetX: nodeX(7), offsetY: nodeY(7), preventDefault() {}});
assert(actions.length === 0, "right-click on empty hole does nothing");
// AUI's button code may be GLFW (right=1) while the DOM convention is right=2; the mask is 2.
actions.length = 0;
onCanvasDown({button: 1, buttons: 2, offsetX: nodeX(2), offsetY: nodeY(2), preventDefault() {}});
assert(actions.length === 1 && actions[0][0] === "remove", "GLFW right button deletes");
actions.length = 0;
onCanvasDown({button: 2, buttons: 4, offsetX: nodeX(2), offsetY: nodeY(2), preventDefault() {}});
assert(actions.length === 0, "middle button is not right-click");

// Mode switches must not resize the canvas (the bug was the panel growing on mode change).
var canvasWidth = canvas.clientWidth;
var canvasHeight = canvas.clientHeight;
setMode("place");
setMode("remove");
setMode("wire");
assert(canvas.clientWidth === canvasWidth && canvas.clientHeight === canvasHeight,
    "canvas client size stable across three mode switches");

console.log("PAGE SMOKE TEST OK");
console.log("actions:", JSON.stringify(actions));
console.log("draw calls:", drawCalls.length);




