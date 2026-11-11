/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.core.map;

import com.google.gson.*;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.storage.compression.Compression;
import de.bluecolored.bluemap.core.storage.file.FileMapStorage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;

/** Offline retained-gallery measurement; never points this at live storage. */
public final class TextureGalleryMigrationBenchmark {
    private static int gzip(byte[] value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(out)) { zip.write(value); }
        return out.size();
    }
    public static void main(String[] args) throws Exception {
        byte[] original = Files.readAllBytes(Path.of(args[0]));
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IOException("Use a new empty benchmark output directory");
        Files.createDirectories(output);
        FileMapStorage storage = new FileMapStorage(output.resolve("map"), Compression.GZIP, true);
        long start = System.nanoTime();
        TextureGallery gallery = TextureGallery.readTexturesFile(new ByteArrayInputStream(original), storage);
        gallery.writeExternalTextures(storage);
        long migrated = System.nanoTime();
        byte[] manifest;
        try (var in = storage.textures().read()) { manifest = in.decompress().readAllBytes(); }
        TextureGallery restored = TextureGallery.readTexturesFile(new ByteArrayInputStream(manifest), storage);
        ByteArrayOutputStream back = new ByteArrayOutputStream();
        restored.writeTexturesFile(back);
        JsonArray input = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonArray();
        JsonArray roundTrip = JsonParser.parseString(back.toString(StandardCharsets.UTF_8)).getAsJsonArray();
        if (!input.equals(roundTrip)) {
            Files.write(output.resolve("round-trip.json"), back.toByteArray());
            throw new IllegalStateException("Retained gallery round-trip differs");
        }
        for (int i = 0; i < input.size(); i++) {
            String key = input.get(i).getAsJsonObject().get("resourcePath").getAsString();
            if (restored.get(new ResourcePath<Texture>(key)) != i) throw new IllegalStateException("Material ordinal changed: " + key);
        }
        long assets;
        try (var files = Files.walk(output.resolve("map/assets"))) { assets = files.filter(Files::isRegularFile).count(); }
        JsonObject report = new JsonObject();
        report.addProperty("entries", input.size());
        report.addProperty("beforeBytes", original.length);
        report.addProperty("afterManifestBytes", manifest.length);
        report.addProperty("beforeGzipBytes", gzip(original));
        report.addProperty("afterManifestGzipBytes", gzip(manifest));
        report.addProperty("uniquePngAssets", assets);
        report.addProperty("migrationMs", (migrated - start) / 1000000.0);
        report.addProperty("roundTripAndOrdinals", true);
        Files.writeString(output.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println(report);
    }
}
