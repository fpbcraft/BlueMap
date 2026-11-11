import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

// Evaluate the owning class without its browser imports; use the actual methods with controlled async IO.
function mapClass() {
    const source = readFileSync(new URL('../src/js/map/Map.js', import.meta.url), 'utf8')
        .replace(/^import[\s\S]*?;\s*/gm, '').replace('export class Map', 'class Map');
    const context = vm.createContext({alert() {}, TileManager: class { constructor() { this.scene = {}; } }, TileLoader: class {}, LowresTileLoader: class {}});
    vm.runInContext(source + '\nglobalThis.BlueMapMap = Map;', context);
    return context.BlueMapMap;
}

test('materials do not mark a map ready before tile managers exist', () => {
    const Map = mapClass();
    const map = Object.create(Map.prototype);
    map.hiresMaterial = [];
    map.lowresMaterial = {};
    map.hiresTileManager = null;
    map.lowresTileManager = null;
    assert.equal(map.isLoaded, false);
    assert.equal(map.terrainHeightAt(0, 0), false);
    assert.doesNotThrow(() => map.loadMapArea(0, 0, 100, 100));
    map.hiresTileManager = {};
    map.lowresTileManager = [];
    assert.equal(map.isLoaded, true);
});

test('an unloaded map cannot install late tile managers from an older load', async () => {
    const Map = mapClass();
    const map = Object.create(Map.prototype);
    Object.assign(map, {loadedTextures: [], animations: [], hiresTileManager: null, lowresTileManager: null,
        hiresMaterial: null, lowresMaterial: null, createLowresMaterial: () => ({dispose() {}}),
        createHiresMaterial: () => [], loadTexturesFile: () => Promise.resolve([])});
    let complete;
    map.loadSettings = () => new Promise(resolve => { complete = resolve; });
    const loading = map.load('', '', '', '', {});
    map.unload();
    complete();
    await assert.rejects(loading, error => error.status === 'cancelled');
    assert.equal(map.hiresTileManager, null);
    assert.equal(map.lowresTileManager, null);
    assert.equal(map.isLoaded, false);
});

function browserClass(file, name, globals = {}) {
    const source = readFileSync(new URL(`../src/js/${file}`, import.meta.url), 'utf8')
        .replace(/^import[\s\S]*?;\s*/gm, '').replace(`export class ${name}`, `class ${name}`);
    const context = vm.createContext(globals);
    vm.runInContext(source + `\nglobalThis.Subject = ${name};`, context);
    return context.Subject;
}

test('a superseded viewer load cannot change the active map or report its failure', async () => {
    const Viewer = browserClass('MapViewer.js', 'MapViewer', {
        HIRES_VERTEX_SHADER: '', HIRES_FRAGMENT_SHADER: '', LOWRES_VERTEX_SHADER: '', LOWRES_FRAGMENT_SHADER: ''
    });
    for (const fail of [false, true]) {
        const viewer = Object.create(Viewer.prototype);
        viewer.map = null;
        viewer.data = {uniforms: {}, mapState: 'idle'};
        let settle;
        const first = {isMap: true, unload() {}, load: () => new Promise((resolve, reject) => { settle = fail ? reject : resolve; })};
        const second = {isMap: true, unload() {}, load: () => new Promise(() => {})};
        const old = viewer.switchMap(first);
        viewer.switchMap(second);
        settle(fail ? new Error('old map failed') : undefined);
        await old;
        assert.equal(viewer.map, second);
        assert.equal(viewer.data.mapState, 'loading');
    }
});

test('a superseded app switch cannot reset the newer map camera or initialize duplicate managers', async () => {
    const App = browserClass('BlueMapApp.js', 'BlueMapApp');
    const app = Object.create(App.prototype);
    const first = {}, second = {};
    app.mapsMap = new Map([['first', first], ['second', second]]);
    const pending = [];
    app.mapViewer = {switchMap: () => new Promise(resolve => pending.push(resolve))};
    let reset = 0, managers = 0;
    app.resetCamera = () => reset++;
    app.updatePageAddress = () => {};
    app.initPlayerMarkerManager = async () => managers++;
    app.initMarkerFileManager = async () => managers++;
    const old = app.switchMap('first');
    const current = app.switchMap('second');
    pending[0]();
    await old;
    assert.equal(reset, 0);
    assert.equal(managers, 0);
    pending[1]();
    await current;
    assert.equal(reset, 1);
    assert.equal(managers, 2);
});
