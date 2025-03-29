/*
   Copyright 2025 Ivan Toshkov

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package io.github.itoshkov.multisearch;


import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.itoshkov.multisearch.MultiSearch.Match;
import io.github.itoshkov.multisearch.StringMultiSearch.Finder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class MultiSearchTest {

    @Test
    void mainCase() {
        final String text = "she sells seashells by the seashore";

        final var multiSearch = new StringMultiSearch<Keyword>()
                .register("she", Keyword.SHE)
                .register("he", Keyword.HE)
                .register("sea", Keyword.SEA)
                .register("ash", Keyword.ASH);

        final var finder = multiSearch.buildFinder();

        final var expected = List.of(new Match<>(0, 3, Keyword.SHE),
                                     new Match<>(1, 2, Keyword.HE),
                                     new Match<>(10, 3, Keyword.SEA),
                                     new Match<>(12, 3, Keyword.ASH),
                                     new Match<>(13, 3, Keyword.SHE),
                                     new Match<>(14, 2, Keyword.HE),
                                     new Match<>(24, 2, Keyword.HE),
                                     new Match<>(27, 3, Keyword.SEA),
                                     new Match<>(29, 3, Keyword.ASH));

        final var matches = finder.searchIn(text).toList();
        assertEquals(expected, matches);
    }

    @Test
    void emptyText() {
        final var multiSearch = new StringMultiSearch<Integer>()
                .register("abc", 0);
        final Finder<Integer> finder = multiSearch.buildFinder();
        final var matches = finder.searchIn("").toList();
        assertEquals(List.of(), matches);
    }

    @Test
    void failIfEmptyKeyword() {
        final StringMultiSearch<Integer> multiSearch = new StringMultiSearch<>();
        assertThrowsExactly(IllegalArgumentException.class, () -> multiSearch.register("", 0));
    }

    @Test
    void noRegisterAfterPrep() {
        final var multiSearch = new StringMultiSearch<Integer>()
                .register("abc", 0);
        final var ignore = multiSearch.buildFinder();
        assertThrowsExactly(IllegalStateException.class, () -> multiSearch.register("bcd", 1));
    }

    @Test
    void finderIsSerializableWhenIdIsSerializable() throws IOException, ClassNotFoundException {
        try (final FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path ser = fs.getPath("/finder.ser");

            // serialize
            {
                final StringMultiSearch<Keyword> multiSearch = new StringMultiSearch<Keyword>()
                        .register("she", Keyword.SHE)
                        .register("he", Keyword.HE)
                        .register("sea", Keyword.SEA)
                        .register("ash", Keyword.ASH);

                final Finder<Keyword> finder = multiSearch.buildFinder();

                try (final OutputStream os = Files.newOutputStream(ser);
                     final ObjectOutputStream oos = new ObjectOutputStream(os)) {

                    oos.writeObject(finder);
                }
            }

            // deserialize
            try (final InputStream is = Files.newInputStream(ser);
                 final ObjectInputStream ois = new ObjectInputStream(is)) {

                @SuppressWarnings("unchecked") final Finder<Keyword> finder = (Finder<Keyword>) ois.readObject();

                final String text = "she sells seashells by the seashore";
                final var expected = List.of(new Match<>(0, 3, Keyword.SHE),
                                             new Match<>(1, 2, Keyword.HE),
                                             new Match<>(10, 3, Keyword.SEA),
                                             new Match<>(12, 3, Keyword.ASH),
                                             new Match<>(13, 3, Keyword.SHE),
                                             new Match<>(14, 2, Keyword.HE),
                                             new Match<>(24, 2, Keyword.HE),
                                             new Match<>(27, 3, Keyword.SEA),
                                             new Match<>(29, 3, Keyword.ASH));

                final var matches = finder.searchIn(text).toList();
                assertEquals(expected, matches);
            }
        }
    }

    public enum Keyword {
        SHE, HE, SEA, ASH
    }
}
