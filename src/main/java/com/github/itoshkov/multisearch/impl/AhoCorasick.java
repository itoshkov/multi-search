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
package com.github.itoshkov.multisearch.impl;


import com.github.itoshkov.multisearch.MultiSearch;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Implement the Aho-Corasick multi-search algorithm.
 *
 * @param <C> The sequence "character" type.
 * @param <T> The sequence ID type.
 */
public class AhoCorasick<C, T> extends MultiSearch<C, T> {
    private static final int ROOT = 0;
    private final List<ProtoVertex<C, T>> vertices;

    public AhoCorasick() {
        this.vertices = new ArrayList<>();
        createVertex(); // Create the root vertex
    }

    @Override
    protected int registerKeyword(@NotNull Iterator<C> keyword, @NotNull Collection<T> keywordIds) {
        ProtoVertex<C, T> current = vertices.get(ROOT);

        int len = 0;
        while (keyword.hasNext()) {
            final C c = keyword.next();
            len++;

            if (!current.children.containsKey(c)) {
                final ProtoVertex<C, T> child = createVertex(current, c);
                current.children.put(c, child);
            }

            current = current.children.get(c);
        }

        current.ids.addAll(keywordIds);
        current.wordLength = len;
        return len;
    }

    private @NotNull ProtoVertex<C, T> createVertex() {
        final ProtoVertex<C, T> vertex = new ProtoVertex<>(vertices.size());
        vertices.add(vertex);
        return vertex;
    }

    private @NotNull ProtoVertex<C, T> createVertex(@NotNull ProtoVertex<C, T> parent, @NotNull C parentChar) {
        final ProtoVertex<C, T> vertex = createVertex();
        vertex.parent = parent;
        vertex.parentChar = parentChar;
        return vertex;
    }

    @Override
    protected @NotNull Finder<C, T> prepareFinder() {
        final List<ProtoVertex<C, T>> protos = this.vertices;
        if (protos == null)
            throw new IllegalStateException("The finder is already prepared.");

        final ProtoVertex<C, T> root = protos.get(ROOT);

        final Queue<ProtoVertex<C, T>> vertexQueue = new LinkedList<>();
        vertexQueue.add(root);
        while (!vertexQueue.isEmpty()) {
            final ProtoVertex<C, T> current = vertexQueue.remove();
            calculateSuffixLink(root, current);
            vertexQueue.addAll(current.children.values());
        }

        final List<Vertex<C, T>> vertices =
                protos.stream()
                      .map(this::toVertex)
                      .toList();

        return new FinderImpl<>(vertices);
    }

    private @NotNull Vertex<C, T> toVertex(@NotNull ProtoVertex<C, T> proto) {
        final Map<C, Integer> children =
                proto.children
                        .entrySet()
                        .stream()
                        .collect(toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().index));
        final int suffixLink = proto.suffixLink.index;
        final int endWordLink = proto.endWordLink.index;

        return new Vertex<>(children, suffixLink, endWordLink, proto.ids, proto.wordLength);
    }

    private void calculateSuffixLink(ProtoVertex<C, T> root, ProtoVertex<C, T> vertex) {
        if (vertex == root) {
            vertex.suffixLink = root;
            vertex.endWordLink = root;
            return;
        }

        if (vertex.parent == root) {
            vertex.suffixLink = root;
            if (vertex.isWord())
                vertex.endWordLink = vertex;
            else
                vertex.endWordLink = vertex.suffixLink.endWordLink;
            return;
        }

        ProtoVertex<C, T> betterVertex = vertex.parent.suffixLink;
        final C chVertex = vertex.parentChar;

        while (true) {
            final ProtoVertex<C, T> child = betterVertex.children.get(chVertex);
            if (child != null) {
                vertex.suffixLink = child;
                break;
            }

            if (betterVertex == root) {
                vertex.suffixLink = root;
                break;
            }

            betterVertex = betterVertex.suffixLink;
        }

        if (vertex.isWord())
            vertex.endWordLink = vertex;
        else
            vertex.endWordLink = vertex.suffixLink.endWordLink;
    }

    private static class ProtoVertex<C, T> {
        private final int index;
        public Map<C, ProtoVertex<C, T>> children;
        public ProtoVertex<C, T> parent;
        public C parentChar;
        public ProtoVertex<C, T> suffixLink;
        public ProtoVertex<C, T> endWordLink;
        public final Set<T> ids;
        public int wordLength;

        public ProtoVertex(int index) {
            this.index = index;
            this.children = new HashMap<>();
            this.ids = new HashSet<>();
            this.parent = null;
            this.suffixLink = null;
            this.endWordLink = null;
            this.wordLength = -1;
        }

        public boolean isWord() {
            return !ids.isEmpty();
        }
    }

    private record Vertex<C, T>(@NotNull Map<C, Integer> children,
                                int suffixLink,
                                int endWordLink,
                                @NotNull Set<T> keywordIds,
                                int length)
            implements Serializable {
    }

    private record FinderImpl<C, T>(@NotNull List<Vertex<C, T>> vertices) implements Finder<C, T> {

        @Override
        public @NotNull Stream<Match<T>> searchIn(@NotNull Iterator<C> text) {
            final Vertex<C, T> root = vertices.get(ROOT);

            return Stream.iterate(new State<>(vertices),
                                  state -> state.hasNext(text),
                                  state -> state.next(text, root))
                         .map(State::match)
                         .filter(Objects::nonNull);
        }
    }

    private record State<C, T>(@NotNull List<Vertex<C, T>> vertices,
                               @NotNull Vertex<C, T> current,
                               int index,
                               @Nullable C atIndex,
                               @Nullable Vertex<C, T> check,
                               @Nullable Match<T> match) {

        public State(List<Vertex<C, T>> vertices) {
            this(vertices, vertices.get(ROOT), 0, null, null, null);
        }

        public boolean hasNext(Iterator<C> text) {
            return check != null || text.hasNext();
        }

        public @NotNull State<C, T> next(Iterator<C> text, Vertex<C, T> root) {
            final C atIndex = State.this.atIndex != null ? State.this.atIndex : text.next();

            Vertex<C, T> current = State.this.current;
            Vertex<C, T> check = State.this.check;

            if (check == null) {
                while (true) {
                    final Vertex<C, T> edge = vertex(current.children.get(atIndex));
                    if (edge != null) {
                        current = edge;
                        break;
                    }

                    if (current == root)
                        break;

                    current = vertex(current.suffixLink);
                }

                check = current;
            }

            check = vertex(check.endWordLink);

            if (check == root)
                return new State<>(vertices, current, index + 1, null, null, null);

            final Match<T> tMatch = constructMatchInfo(check);

            return new State<>(vertices, current, index, atIndex, vertex(check.suffixLink), tMatch);
        }

        private @NotNull Match<T> constructMatchInfo(@NotNull Vertex<C, T> vertex) {
            final int length = vertex.length;
            final int indexOfMatch = index + 1 - length;
            final Set<T> keywordIds = vertex.keywordIds;
            return new Match<>(indexOfMatch, length, keywordIds);
        }

        @Contract("null -> null; !null -> !null")
        private Vertex<C, T> vertex(Integer index) {
            return index != null ? vertices.get(index) : null;
        }
    }
}
