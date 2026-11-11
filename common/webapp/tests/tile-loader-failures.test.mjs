import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

function subject(name, globals) {
    const source = readFileSync(new URL(`../src/js/map/${name}.js`, import.meta.url), 'utf8')
        .replace(/^import[\s\S]*?;\s*/gm, '').replace(/    constructor\([\s\S]*?\n    }\n/, '').replace(`export class ${name}`, `class ${name}`);
    const context = vm.createContext({pathFromCoords: () => 'tile', ...globals});
    vm.runInContext(source + `\nglobalThis.Subject = ${name};`, context);
    return new context.Subject();
}

test('hires parse/hydration failures reject instead of leaving a terrain slot pending', async () => {
    const loader = subject('TileLoader', {});
    Object.assign(loader, {tilePath: '/', tileCacheHash: 0, loadBlocker: async () => {},
        fileLoader: {load: (_url, loaded) => loaded(new ArrayBuffer(1))},
        bufferGeometryLoader: {parse: () => { throw Error('invalid PRBM'); }}});
    await assert.rejects(loader.load(0, 0), /invalid PRBM/);
    let disposed = 0;
    loader.bufferGeometryLoader.parse = () => ({groups: [], dispose: () => disposed++});
    loader.material = [];
    loader.material.ensureMaterials = async () => { throw Error('missing PNG'); };
    await assert.rejects(loader.load(0, 0), /missing PNG/);
    assert.equal(disposed, 1);
});

test('lowres blocker failure disposes the texture and releases the pending load', async () => {
    const loader = subject('LowresTileLoader', {
        NearestFilter: 1, NearestMipMapLinearFilter: 2, ClampToEdgeWrapping: 3,
    });
    let disposed = 0;
    Object.assign(loader, {tilePath: '/', tileCacheHash: 0, lod: 1,
        loadBlocker: async () => { throw Error('fixture blocker'); },
        textureLoader: {load: (_url, loaded) => loaded({dispose: () => disposed++})}});
    await assert.rejects(loader.load(0, 0), /fixture blocker/);
    assert.equal(disposed, 1);
});
