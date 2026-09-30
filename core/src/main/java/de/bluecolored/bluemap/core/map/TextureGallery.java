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
import de.bluecolored.bluemap.core.resources.adapter.ResourcesGson;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.storage.ItemStorage;
import de.bluecolored.bluemap.core.storage.MapStorage;
import de.bluecolored.bluemap.core.util.Key;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TextureGallery {

    private static final Gson GSON = ResourcesGson.addAdapter(new GsonBuilder())
            .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
            .create();

    private static final String PNG_DATA_PREFIX = "data:image/png;base64,";
    private static final String TEXTURE_ASSET_PREFIX = "textures/";

    private final Map<ResourcePath<Texture>, TextureMapping> textureMappings;
    private int nextId;

    public TextureGallery() {
        this.textureMappings = new HashMap<>();
        this.nextId = 0;
    }

    public void clear() {
        this.textureMappings.clear();
        this.nextId = 0;
    }

    public int get(@Nullable ResourcePath<Texture> textureResourcePath) {
        if (textureResourcePath == null) textureResourcePath = ResourcePack.MISSING_TEXTURE;
        TextureMapping mapping = textureMappings.get(textureResourcePath);
        return mapping != null ? mapping.getId() : 0;
    }

    public synchronized void put(ResourcePath<Texture> textureResourcePath) {
        textureMappings.compute(textureResourcePath, (r, mapping) -> {
            if (mapping == null)
                return new TextureMapping(nextId++, textureResourcePath.getResource());

            Texture texture = textureResourcePath.getResource();
            if (texture != null) mapping.setTexture(texture);
            return mapping;
        });
    }

    public synchronized void put(ResourcePack resourcePack) {
        this.put(ResourcePack.MISSING_TEXTURE); // put this first
        resourcePack.getTextures().keySet()
                .stream()
                .sorted(Comparator
                        .comparing((ResourcePath<Texture> r) ->  {
                            Texture texture = r.getResource(resourcePack::getTexture);
                            return texture != null && texture.getColorPremultiplied().a < 1f;
                        })
                        .thenComparing(Key::getFormatted))
                .forEach(this::put);
    }

    public void writeTexturesFile(OutputStream out) throws IOException {
        Texture[] textures = snapshotTextures();

        try (Writer writer = new OutputStreamWriter(out)) {
            GSON.toJson(textures, Texture[].class, writer);
        } catch (JsonIOException ex) {
            throw new IOException(ex);
        }
    }

    /**
     * Writes a lightweight texture manifest and stores each PNG as a content-addressed
     * map asset. The manifest keeps material indices stable while allowing the webapp
     * to fetch/decode only textures referenced by visible PRBM tiles.
     */
    public void writeExternalTextures(MapStorage storage) throws IOException {
        Texture[] textures = snapshotTextures();
        JsonArray manifest = new JsonArray();
        Set<String> ensuredAssets = new HashSet<>();

        for (Texture texture : textures) {
            JsonObject entry = GSON.toJsonTree(texture, Texture.class).getAsJsonObject();
            byte[] png = decodePng(texture);
            String hash = sha256(png);
            String shard = hash.substring(0, 2);
            String assetName = TEXTURE_ASSET_PREFIX + shard + "/" + hash + ".png";

            if (ensuredAssets.add(assetName)) {
                ItemStorage asset = storage.asset(assetName);
                if (!asset.exists()) {
                    try (OutputStream out = asset.write()) {
                        out.write(png);
                    }
                }
            }

            entry.remove("texture");
            entry.addProperty("textureUrl", "assets/" + assetName);
            manifest.add(entry);
        }

        try (OutputStream out = storage.textures().write();
             Writer writer = new OutputStreamWriter(out)) {
            GSON.toJson(manifest, writer);
        } catch (JsonIOException ex) {
            throw new IOException(ex);
        }
    }

    private Texture[] snapshotTextures() {
        Texture[] textures = new Texture[nextId];
        Arrays.fill(textures, Texture.MISSING);

        this.textureMappings.forEach((textureResourcePath, mapping) -> {
            int ordinal = mapping.getId();
            Texture texture = mapping.getTexture();
            if (texture == null || texture.getTexture() == null)
                texture = Texture.missing(textureResourcePath);
            textures[ordinal] = texture;
        });

        return textures;
    }

    private static byte[] decodePng(Texture texture) throws IOException {
        String encoded = texture.getTexture();
        if (encoded == null || !encoded.startsWith(PNG_DATA_PREFIX))
            encoded = Texture.MISSING.getTexture();

        try {
            return Base64.getDecoder().decode(encoded.substring(PNG_DATA_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid base64 PNG texture data", ex);
        }
    }

    private static String sha256(byte[] data) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 is unavailable", ex);
        }
    }

    public static TextureGallery readTexturesFile(InputStream in) throws IOException {
        TextureGallery gallery = new TextureGallery();
        try (Reader reader = new InputStreamReader(in)) {
            Texture[] textures = GSON.fromJson(reader, Texture[].class);
            if (textures == null) throw new IOException("Texture data is empty!");
            gallery.nextId = textures.length;
            for (int ordinal = 0; ordinal < textures.length; ordinal++) {
                Texture texture = textures[ordinal];
                if (texture != null) {
                    gallery.textureMappings.put(texture.getResourcePath(), new TextureMapping(ordinal, texture));
                }
            }
        } catch (JsonParseException ex) {
            throw new IOException(ex);
        }
        return gallery;
    }

    static class TextureMapping {
        private final int id;
        private @Nullable Texture texture;

        public TextureMapping(int id, @Nullable Texture texture) {
            this.id = id;
            this.texture = texture;
        }

        public int getId() {
            return id;
        }

        public @Nullable Texture getTexture() {
            return texture;
        }

        public void setTexture(@Nullable Texture texture) {
            this.texture = texture;
        }

    }

}
