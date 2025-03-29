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


import io.github.itoshkov.multisearch.impl.AhoCorasick;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Base class for MultiSearch implementations.
 * <p>The usage pattern is:</p>
 * <ol>
 *     <li>Create a multi-search implementation - {@link #implementation(Algorithm)} or {@link #implementation()}</li>
 *     <li>Register keywords to search for - {@link #register(Iterator, Object[])} or
 *     {@link #register(Iterator, Collection)}</li>
 *     <li>Build the finder - {@link #buildFinder()}</li>
 *     <li>Use the finder - {@link Finder#searchIn(Iterator)}</li>
 * </ol>
 * <p>The MultiSearch class is thread-safe but its methods are blocking. The finder class is thread-safe.</p>
 *
 * @param <C> The sequence "character" type.
 * @param <T> The sequence ID type.
 */
public abstract class MultiSearch<C, T> {
    private final Lock lock = new ReentrantLock();
    private final Set<T> usedIds = new HashSet<>();
    private boolean open = true;

    /**
     * Create a new MultiSearch instance using the specified algorithm.
     *
     * @param algorithm The algorithm.
     * @param <C>       The sequence "character" type.
     * @param <T>       The sequence ID type.
     * @return The MultiSearch instance.
     */
    @Contract("_ -> new")
    public static <C, T> @NotNull MultiSearch<C, T> implementation(@NotNull Algorithm algorithm) {
        return switch (algorithm) {
            case AHO_CORASICK -> new AhoCorasick<>();
        };
    }

    /**
     * Create a new MultiSearch instance using the default algorithm.
     *
     * @param <C> The sequence "character" type.
     * @param <T> The sequence ID type.
     * @return The MultiSearch instance.
     */
    @Contract("-> new")
    public static <C, T> @NotNull MultiSearch<C, T> implementation() {
        return implementation(Algorithm.AHO_CORASICK);
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
    @SuppressWarnings("unused")
    @Blocking
    @SafeVarargs
    public final MultiSearch<C, T> register(@NotNull Iterator<C> keyword, @NotNull T... keywordIds)
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
    public final MultiSearch<C, T> register(@NotNull Iterator<C> keyword, @NotNull Collection<T> keywordIds)
            throws IllegalArgumentException, IllegalStateException {

        lock.lock();
        try {
            if (!open)
                throw new IllegalStateException("Cannot register keywords after building a finder.");

            if (keywordIds.isEmpty())
                throw new IllegalArgumentException("No keyword IDs provided.");

            for (T id : keywordIds)
                if (!usedIds.add(id))
                    throw new IllegalStateException("Duplicate keyword ID: " + id);

            final int len = registerKeyword(keyword, keywordIds);
            if (len == 0)
                throw new IllegalArgumentException("Cannot register empty keyword");

            return this;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Register a search keyword with the specified IDs.
     *
     * @param keyword    The keyword.
     * @param keywordIds The IDs.
     * @return The keyword length
     */
    protected abstract int registerKeyword(@NotNull Iterator<C> keyword, @NotNull Collection<T> keywordIds);

    /**
     * Close the registration of new keywords and build a finder.
     *
     * @return The finder.
     * @throws IllegalStateException if the finder was already built.
     */
    @Blocking
    public @NotNull Finder<C, T> buildFinder() throws IllegalStateException {
        lock.lock();
        try {
            if (!open)
                throw new IllegalStateException("Cannot register keywords after building a finder.");

            open = false;

            return prepareFinder();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Create the finder implementation.
     *
     * @return The finder implementation.
     */
    @NotNull
    protected abstract Finder<C, T> prepareFinder();

    /**
     * The multi-search algorithm.
     */
    public enum Algorithm {
        /**
         * The Aho-Corasick algorithm.
         */
        AHO_CORASICK
    }

    /**
     * The finder.
     *
     * @param <C> The sequence "character" type.
     * @param <T> The sequence ID type.
     */
    @SuppressWarnings("unused")
    public interface Finder<C, T> extends Serializable {
        /**
         * Search for matches in the provided sequence.
         *
         * @param sequence The sequence to search in (the "hay").
         * @return A sequential stream of matches.
         */
        @NonBlocking
        @NotNull Stream<Match<T>> searchIn(@NotNull Iterator<C> sequence);

        /**
         * Search for matches in the provided sequence.
         *
         * @param sequence The sequence to search in (the "hay").
         * @return A sequential stream of matches.
         */
        @NonBlocking
        default Stream<Match<T>> searchIn(@NotNull Collection<C> sequence) {
            return searchIn(sequence.iterator());
        }

        /**
         * Search for matches in the provided sequence.
         *
         * @param sequence The sequence to search in (the "hay").
         * @return A sequential stream of matches.
         */
        @NonBlocking
        default Stream<Match<T>> searchIn(@NotNull C[] sequence) {
            return searchIn(Arrays.asList(sequence));
        }
    }

    /**
     * A match.
     *
     * @param startInclusive The start index, where the match is found.
     * @param length         The length of the match.
     * @param keywordIds     The keywords, associated with the match.
     */
    public record Match<T>(int startInclusive, int length, @NotNull Set<T> keywordIds) {

        /**
         * Construct a new match.
         *
         * @param startInclusive The start index.
         * @param length         The keyword length.
         * @param keywordIds     The keyword IDs.
         */
        @SafeVarargs
        public Match(int startInclusive, int length, @NotNull T... keywordIds) {
            this(startInclusive, length, Set.of(keywordIds));
        }

        /**
         * Returns the index in the sequence right after the match.
         *
         * @return The index in the sequence right after the match.
         */
        @SuppressWarnings("unused")
        public int endExclusive() {
            return startInclusive + length;
        }
    }
}
