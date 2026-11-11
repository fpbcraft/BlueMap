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

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.storage.ItemStorage;
import de.bluecolored.bluemap.core.storage.compression.Compression;
import de.bluecolored.bluemap.core.storage.file.FileMapStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class TextureGalleryTest {
    @TempDir Path root;

    private TextureGallery gallery() throws IOException {
        TextureGallery gallery = new TextureGallery();
        for (String name : new String[]{"retained_one", "retained_two"}) {
            ResourcePath<Texture> path = new ResourcePath<>("test", name);
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++)
                image.setRGB(x, y, name.endsWith("one") ? 0xff102030 : 0x808090a0);
            path.setResource(Texture.from(path, image));
            gallery.put(path);
        }
        return gallery;
    }

    private byte[] embedded(TextureGallery gallery) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        gallery.writeTexturesFile(out);
        return out.toByteArray();
    }

    private byte[] manifest(FileMapStorage storage) throws IOException {
        try (var in = storage.textures().read()) { return in.decompress().readAllBytes(); }
    }

    @Test void migratesLegacyAndRestoresRetainedTexturesWithoutAResourcePack() throws Exception {
        FileMapStorage storage = new FileMapStorage(root, Compression.GZIP, true);
        byte[] original = embedded(gallery());
        TextureGallery loaded = TextureGallery.readTexturesFile(new ByteArrayInputStream(original), storage);
        loaded.writeExternalTextures(storage);
        byte[] firstManifest = manifest(storage);
        JsonArray external = JsonParser.parseString(new String(firstManifest, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
        assertEquals(2, external.size());
        for (var entry : external) {
            assertFalse(entry.getAsJsonObject().has("texture"));
            assertTrue(entry.getAsJsonObject().get("textureUrl").getAsString().startsWith("assets/textures/"));
        }
        TextureGallery restored = TextureGallery.readTexturesFile(new ByteArrayInputStream(firstManifest), storage);
        assertEquals(0, restored.get(new ResourcePath<Texture>("test", "retained_one")));
        assertEquals(1, restored.get(new ResourcePath<Texture>("test", "retained_two")));
        assertArrayEquals(original, embedded(restored), "PNG bytes, alpha/color and ordinals survive a restart without current pack textures");
        restored.writeExternalTextures(storage);
        assertArrayEquals(firstManifest, manifest(storage));
    }

    @Test void refusesMissingOrChangedAssetsInsteadOfResettingMaterialIds() throws Exception {
        FileMapStorage storage = new FileMapStorage(root, Compression.NONE, true);
        gallery().writeExternalTextures(storage);
        byte[] original = manifest(storage);
        String url = JsonParser.parseString(new String(original)).getAsJsonArray().get(0).getAsJsonObject().get("textureUrl").getAsString();
        Files.write(storage.getAssetPath(url.substring("assets/".length())), new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> TextureGallery.readTexturesFile(new ByteArrayInputStream(original), storage));
        assertArrayEquals(original, manifest(storage));
        Files.delete(storage.getAssetPath(url.substring("assets/".length())));
        assertThrows(IOException.class, () -> TextureGallery.readTexturesFile(new ByteArrayInputStream(original), storage));
    }

    @Test void retainsFlatGalleryAnimationMetadataAcrossBothFormats() throws Exception {
        FileMapStorage storage = new FileMapStorage(root, Compression.NONE, true);
        JsonArray entries = JsonParser.parseString(new String(embedded(gallery()))).getAsJsonArray();
        entries.get(0).getAsJsonObject().add("animation", JsonParser.parseString("""
                {"interpolate":true,"width":2,"height":3,"frametime":4,
                 "frames":[{"index":2,"time":5},{"index":0,"time":8}]}
                """));
        TextureGallery loaded = TextureGallery.readTexturesFile(new ByteArrayInputStream(entries.toString().getBytes()), storage);
        loaded.writeExternalTextures(storage);
        TextureGallery restored = TextureGallery.readTexturesFile(new ByteArrayInputStream(manifest(storage)), storage);
        assertEquals(entries, JsonParser.parseString(new String(embedded(restored))));
    }

    @Test void repairsInterruptedAssetWritesBeforePublishingTheirReferences() throws Exception {
        FileMapStorage storage = new FileMapStorage(root, Compression.NONE, true);
        TextureGallery gallery = gallery();
        gallery.writeExternalTextures(storage);
        byte[] original = manifest(storage);
        String url = JsonParser.parseString(new String(original)).getAsJsonArray().get(0).getAsJsonObject().get("textureUrl").getAsString();
        Files.write(storage.getAssetPath(url.substring("assets/".length())), new byte[]{1, 2, 3});
        gallery.writeExternalTextures(storage);
        assertArrayEquals(embedded(gallery), embedded(TextureGallery.readTexturesFile(new ByteArrayInputStream(manifest(storage)), storage)));
    }

    @Test void assetFailureLeavesTheExistingManifestUntouched() throws Exception {
        FileMapStorage storage = new FileMapStorage(root, Compression.NONE, true) {
            @Override public ItemStorage asset(String name) {
                return new ItemStorage() {
                    public OutputStream write() throws IOException { throw new IOException("simulated disk failure"); }
                    public de.bluecolored.bluemap.core.storage.compression.CompressedInputStream read() { return null; }
                    public boolean exists() { return false; }
                    public void delete() {}
                    public boolean isClosed() { return false; }
                };
            }
        };
        byte[] previous = embedded(gallery());
        try (var out = storage.textures().write()) { out.write(previous); }
        assertThrows(IOException.class, () -> gallery().writeExternalTextures(storage));
        assertArrayEquals(previous, manifest(storage));
    }

    @Test void repairsAnUnreadableCompressedAssetBeforePublishing() throws Exception {
        FileMapStorage storage = new FileMapStorage(root, Compression.GZIP, true);
        TextureGallery gallery = gallery();
        gallery.writeExternalTextures(storage);
        byte[] original = manifest(storage);
        String url = JsonParser.parseString(new String(original)).getAsJsonArray().get(0).getAsJsonObject().get("textureUrl").getAsString();
        Path asset;
        try (var files = Files.walk(root)) {
            String hash = url.substring(url.lastIndexOf('/') + 1);
            asset = files.filter(path -> path.getFileName().toString().startsWith(hash)).findFirst().orElseThrow();
        }
        Files.write(asset, new byte[]{0x1f, (byte) 0x8b});
        gallery.writeExternalTextures(storage);
        assertArrayEquals(embedded(gallery), embedded(TextureGallery.readTexturesFile(new ByteArrayInputStream(manifest(storage)), storage)));
    }

    @Test void rejectsExternalPathsOutsideTheContentAddressedAssetDirectory() throws Exception {
        FileMapStorage storage = new FileMapStorage(root, Compression.NONE, true);
        gallery().writeExternalTextures(storage);
        JsonArray entries = JsonParser.parseString(new String(manifest(storage))).getAsJsonArray();
        entries.get(0).getAsJsonObject().addProperty("textureUrl", "assets/textures/../../registry.json");
        assertThrows(IOException.class, () -> TextureGallery.readTexturesFile(new ByteArrayInputStream(entries.toString().getBytes()), storage));
    }
}
