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
package de.bluecolored.bluemap.core.storage.file;

import de.bluecolored.bluemap.core.storage.compression.Compression;
import de.bluecolored.bluemap.core.util.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FileItemStorageTest {
    @TempDir Path root;

    @Test void failedCompressionCloseDoesNotReplaceThePreviousPayload() throws Exception {
        Path file = root.resolve("textures.json");
        byte[] previous = "previous complete manifest".getBytes();
        Files.write(file, previous);
        Compression failure = new Compression() {
            public Key getKey() { return Key.bluemap("test"); }
            public String getId() { return "test"; }
            public String getFileSuffix() { return ""; }
            public InputStream decompress(InputStream in) { return in; }
            public OutputStream compress(OutputStream out) {
                return new FilterOutputStream(out) {
                    @Override public void close() throws IOException { super.close(); throw new IOException("simulated close failure"); }
                };
            }
        };
        assertThrows(IOException.class, () -> new FileItemStorage(file, failure, true).write("new manifest".getBytes()));
        assertArrayEquals(previous, Files.readAllBytes(file));
        try (var files = Files.list(root)) { assertEquals(1, files.count(), "failed staged files are cleaned up"); }
    }

    @Test void completeCompressedPayloadReplacesAtomically() throws Exception {
        Path file = root.resolve("textures.json.gz");
        FileItemStorage storage = new FileItemStorage(file, Compression.GZIP, true);
        storage.write("new manifest".getBytes());
        try (var in = storage.read()) { assertEquals("new manifest", new String(in.decompress().readAllBytes())); }
    }
}
