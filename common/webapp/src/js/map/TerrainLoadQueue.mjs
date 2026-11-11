/** Shared budget across every LOD and map; live addons do not wait on this queue. */
export class TerrainLoadQueue {
    constructor(limitPerKind = 2) {
        this.limitPerKind = limitPerKind;
        this.active = {lowres: 0, hires: 0};
        this.jobs = [];
        this.scheduled = false;
    }

    schedule(kind, priority, cancelled, load) {
        const promise = new Promise((resolve, reject) => {
            this.jobs.push({kind, priority, cancelled, load, resolve, reject});
        });
        if (!this.scheduled) {
            this.scheduled = true;
            queueMicrotask(() => { this.scheduled = false; this.drain(); });
        }
        return promise;
    }

    prune() {
        this.jobs = this.jobs.filter(job => {
            if (!job.cancelled()) return true;
            job.reject({status: 'cancelled'});
            return false;
        });
    }

    drain() {
        this.prune();
        while (true) {
            let index = -1;
            let priority = Infinity;
            for (let i = 0; i < this.jobs.length; i++) {
                const job = this.jobs[i];
                if (this.active[job.kind] >= this.limitPerKind) continue;
                const score = job.priority();
                if (score < priority) { priority = score; index = i; }
            }
            if (index < 0) return;
            const [job] = this.jobs.splice(index, 1);
            this.active[job.kind]++;
            Promise.resolve().then(() => {
                if (job.cancelled()) throw {status: 'cancelled'};
                return job.load();
            }).then(job.resolve, job.reject).finally(() => {
                this.active[job.kind]--;
                this.drain();
            });
        }
    }
}

export const terrainLoads = new TerrainLoadQueue();
