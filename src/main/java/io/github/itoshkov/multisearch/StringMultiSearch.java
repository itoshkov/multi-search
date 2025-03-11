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


import io.github.itoshkov.multisearch.MultiSearch.Algorithm;
import io.github.itoshkov.multisearch.MultiSearch.Match;
import io.github.itoshkov.multisearch.utils.StringIterator;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * A MultiSearch for finding substrings in a string.
 *
 * @param <T> The substring ID type.
 */
public class StringMultiSearch<T> {
    private final MultiSearch<Character, T> multiSearch;

    /**
     * Create a new String multi-search using the default algorithm.
     *
     * @see MultiSearch#implementation()
     */
    public StringMultiSearch() {
        this.multiSearch = MultiSearch.implementation();
    }

    /**
     * Create a new String multi-search using the specified algorithm.
     *
     * @param algorithm The multi-search algorithm.
     */
    @SuppressWarnings("unused")
    public StringMultiSearch(@NotNull Algorithm algorithm) {
        this.multiSearch = MultiSearch.implementation(algorithm);
    }

    /**
     * Register a search keyword with the specified IDs.
     *
     * @param keyword    The keyword.
     * @param keywordIds The IDs.
     * @return This.
     * @throws IllegalArgumentException if no IDs were provided or if the keyword is emtpy.
     * @throws IllegalStateException    if finder was already built, or if an ID was already used.
     */
    @Blocking
    @SafeVarargs
    public final StringMultiSearch<T> register(@NotNull String keyword, @NotNull T... keywordIds)
            throws IllegalArgumentException, IllegalStateException {

        return register(keyword, List.of(keywordIds));
    }

    /**
     * Register a search keyword with the specified IDs.
     *
     * @param keyword    The keyword.
     * @param keywordIds The IDs.
     * @return This.
     * @throws IllegalArgumentException if no IDs were provided or if the keyword is emtpy.
     * @throws IllegalStateException    if finder was already built, or if an ID was already used.
     */
    @Blocking
    public StringMultiSearch<T> register(@NotNull String keyword, @NotNull Collection<T> keywordIds)
            throws IllegalArgumentException, IllegalStateException {

        multiSearch.register(new StringIterator(keyword), keywordIds);
        return this;
    }

    /**
     * Close the registration of new keywords and build a finder.
     *
     * @return The finder.
     * @throws IllegalStateException if the finder was already built.
     */
    @Blocking
    public @NotNull Finder<T> buildFinder() throws IllegalStateException {
        final MultiSearch.Finder<Character, T> baseFinder = multiSearch.buildFinder();
        return new Finder<>(baseFinder);
    }

    /**
     * The finder.
     *
     * @param <T> The sequence ID type.
     */
    public static final class Finder<T> implements Serializable {

        /**
         * The underlying finder.
         */
        private final MultiSearch.Finder<Character, T> finder;

        private Finder(MultiSearch.Finder<Character, T> finder) {
            this.finder = finder;
        }

        /**
         * Search for matches in the provided sequence.
         *
         * @param text The text to search in (the "hay").
         * @return A sequential stream of matches.
         */
        @NonBlocking
        public @NotNull Stream<Match<T>> searchIn(@NotNull String text) {
            return finder.searchIn(new StringIterator(text));
        }
    }
}
