/** Only explicitly opted-in addons execute before map initialization. */
export class AddonScriptLoader {
    constructor(document, onLoad = () => {}) {
        this.document = document;
        this.onLoad = onLoad;
        this.loaded = new Set();
    }

    load(scripts = [], earlyPatterns = [], early = false) {
        const patterns = earlyPatterns.map(pattern => new RegExp("^" + pattern.split("*")
            .map(part => part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join(".*") + "$"));
        for (const url of scripts) {
            const isEarly = patterns.some(pattern => pattern.test(url));
            if (isEarly !== early) continue;
            const canonical = new URL(url, this.document.baseURI).href;
            if (this.loaded.has(canonical)) continue;
            this.loaded.add(canonical);
            const script = this.document.createElement("script");
            script.src = canonical;
            if (early) script.async = false;
            script.onerror = () => this.loaded.delete(canonical);
            this.onLoad(url);
            this.document.body.appendChild(script);
        }
    }
}
