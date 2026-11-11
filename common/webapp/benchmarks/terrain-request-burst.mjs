// Exercise the actual TileManager code with stalled IO and a deterministic clock.
import {readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import vm from 'node:vm';
import {TerrainLoadQueue} from '../src/js/map/TerrainLoadQueue.mjs';
const file = 'common/webapp/src/js/map/TileManager.js';
const baseline = process.argv[2] || 'f548cd8';
for (const label of ['before', 'after']) {
    const source = (label === 'before' ? execFileSync('git', ['show', `${baseline}:${file}`], {encoding: 'utf8'}) : readFileSync(new URL('../src/js/map/TileManager.js', import.meta.url), 'utf8'))
        .replace(/^import[\s\S]*?;\s*/gm, '').replace('export class TileManager', 'class TileManager');
    let time = 0, next = 0, requests = 0;
    const timers = new Map();
    const queue = new TerrainLoadQueue();
    class Group { add() {} }
    class Vector2 { constructor(x, y) { this.x = x; this.y = y; } set(x, y) { this.x = x; this.y = y; } }
    class TileMap { setAll() {} setTile() {} }
    class Tile { constructor(x, z) { this.x = x; this.z = z; } load() { requests++; return new Promise(() => {}); } unload() {} }
    const context = vm.createContext({Group, Scene: Group, Vector2, TileMap, Tile, terrainLoads: queue,
        hashTile: (x, z) => `${x}:${z}`, dispatchEvent() {},
        setTimeout(fn, delay) { const id = ++next; timers.set(id, {fn, at: time + delay}); return id; },
        clearTimeout(id) { timers.delete(id); }});
    vm.runInContext(source + '\nglobalThis.Subject = TileManager;', context);
    const managers = [3, 2, 1, 0].map(lod => new context.Subject({isLowresTileLoader: lod > 0, lod}));
    for (const manager of managers) manager.loadAroundTile(0, 0, 4, 4);
    while (true) {
        const timer = [...timers].filter(([, task]) => task.at <= 250).sort((a, b) => a[1].at - b[1].at)[0];
        if (!timer) break;
        timers.delete(timer[0]); time = timer[1].at; timer[1].fn();
        await new Promise(resolve => setImmediate(resolve));
    }
    await new Promise(resolve => setImmediate(resolve));
    console.log(JSON.stringify({label, stalledTileRequestsAt250ms: requests, waiting: queue.jobs.length}));
}
