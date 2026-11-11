import {test} from 'node:test';
import assert from 'node:assert/strict';
import {TerrainLoadQueue} from '../src/js/map/TerrainLoadQueue.mjs';
const flush = () => new Promise(resolve => setImmediate(resolve));

test('shared budgets reserve progress for both coverage and detail across managers', async () => {
    const queue = new TerrainLoadQueue();
    const finish = [];
    const started = [];
    const jobs = [];
    for (let i = 0; i < 6; i++) {
        jobs.push(queue.schedule('lowres', () => i, () => false, () => new Promise(resolve => {
            started.push(`lowres${i}`); finish.push(resolve);
        })));
    }
    for (let i = 0; i < 4; i++) {
        jobs.push(queue.schedule('hires', () => i, () => false, () => new Promise(resolve => {
            started.push(`hires${i}`); finish.push(resolve);
        })));
    }
    await flush();
    assert.deepEqual(started, ['lowres0', 'hires0', 'lowres1', 'hires1']);
    assert.deepEqual(queue.active, {lowres: 2, hires: 2});
    while (started.length < jobs.length || finish.length) {
        finish.splice(0).forEach(resolve => resolve());
        await flush();
        assert.ok(queue.active.lowres <= 2 && queue.active.hires <= 2);
    }
    await Promise.all(jobs);
});

test('unloaded queued tiles never start IO; failures release a budget slot', async () => {
    const queue = new TerrainLoadQueue(1);
    let finish;
    const first = queue.schedule('hires', () => 0, () => false, () => new Promise(resolve => { finish = resolve; }));
    await flush();
    let obsolete = false;
    let reads = 0;
    const stale = queue.schedule('hires', () => -1, () => obsolete, async () => { reads++; });
    const rejected = assert.rejects(stale, error => error.status === 'cancelled');
    obsolete = true;
    queue.prune();
    await rejected;
    const failed = queue.schedule('hires', () => 0, () => false, async () => { throw Error('fixture'); });
    const failure = assert.rejects(failed, /fixture/);
    const next = queue.schedule('hires', () => 1, () => false, async () => { reads++; });
    finish();
    await Promise.all([first, failure, next]);
    assert.equal(reads, 1);
});

test('queued priority uses the latest camera position', async () => {
    const queue = new TerrainLoadQueue(1);
    let finish;
    const first = queue.schedule('lowres', () => 0, () => false, () => new Promise(resolve => { finish = resolve; }));
    await flush();
    let camera = 0;
    const started = [];
    const jobs = [1, 10].map(x => queue.schedule('lowres', () => Math.abs(x - camera), () => false, async () => { started.push(x); }));
    camera = 10;
    finish();
    await Promise.all([first, ...jobs]);
    assert.deepEqual(started, [10, 1]);
});
