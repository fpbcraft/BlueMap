import {test} from "node:test";
import assert from "node:assert/strict";
import {AddonScriptLoader} from "../src/js/AddonScriptLoader.mjs";

test("opted-in live scripts start first, late scripts and reloads do not duplicate", () => {
    const nodes = [];
    const document = {baseURI: "https://example.com/map/", createElement: () => ({}),
        body: {appendChild: node => nodes.push(node)}};
    const loader = new AddonScriptLoader(document);
    const scripts = ["player-history/player-history-2.5.0.js", "other.js", "assets/bluemap3d/bluemap3d.js"];
    const patterns = ["player-history/player-history-*.js", "assets/bluemap3d/bluemap3d.js"];
    loader.load(scripts, patterns, true);
    assert.equal(nodes.length, 2);
    assert.ok(nodes.every(node => node.async === false));
    loader.load(scripts, patterns);
    loader.load(scripts, patterns, true);
    loader.load(scripts, patterns);
    assert.equal(nodes.length, 3);
    assert.equal(nodes[2].src, "https://example.com/map/other.js");
});

test("existing scripts retain late execution when configuration is absent", () => {
    const nodes = [];
    const loader = new AddonScriptLoader({baseURI: "https://example.com/", createElement: () => ({}),
        body: {appendChild: node => nodes.push(node)}});
    loader.load(["legacy.js"], undefined, true);
    assert.equal(nodes.length, 0);
    loader.load(["legacy.js"]);
    assert.equal(nodes.length, 1);
    assert.equal(nodes[0].async, undefined);
    nodes[0].onerror();
    loader.load(["legacy.js"]);
    assert.equal(nodes.length, 2);
});

test("wildcards escape literal URL punctuation and normalize duplicate URLs", () => {
    const nodes = [];
    const loader = new AddonScriptLoader({baseURI: "https://example.com/", createElement: () => ({}),
        body: {appendChild: node => nodes.push(node)}});
    const scripts = ["addon.v1.js", "addonXv1Xjs", "./addon.v1.js"];
    loader.load(scripts, ["addon.*.js", "./addon.*.js"], true);
    assert.equal(nodes.length, 1);
    assert.equal(nodes[0].src, "https://example.com/addon.v1.js");
});
