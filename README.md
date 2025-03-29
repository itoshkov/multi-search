# multi-search - find all needles in the haystack
A Java library for searching for multiple keywords at the same
time. Currently, only the Aho-Corasick algorithm is implemented.

## Overview

The library provides a generic interface, which allows to search for
subsequences in a sequence. The sequence is traversed only once.

The library also provides a convenience API for working with strings
-- the `StringMultiSearch` class.

The library requires Java 17 or later. It is the earliest LTS with
Java records, and they have a straightforward serialization story.

### Usage

#### Preparation

Let's say you want to find all the matches for the strings "she",
"he", "sea" and "ash" in some text. First, we need to register these
keywords:

```java
// We are using Integer keyword IDs.
StringMultiSearch<Integer> multiSearch = new StringMultiSearch<>();
multiSearch.register("she", 0);
multiSearch.registre("he", 1);
multiSearch.register("sea", 2);
multiSearch.regsiter("ash", 3);
```

We associate an ID with each keyword. These are later returned as part
of the result, to indicate which keyword was found.

Next, we build the finder:
```java
Finder<Integer> finder = multiSearch.buildFinder();
```

#### Search

We can now use it to find all the occurrences of the keywords in the
text:

```java
List<Match<Integer>> allMatches =
        finder.matchIn("she sells seashells by the seashore")
		      .toList();
```

And that's it! Let's print a couple of matches to see what they look
like:

```
Match[startInclusive=0, length=3, keywordIds=[0]]
Match[startInclusive=1, length=2, keywordIds=[1]]
```

We can see a few interesting things. First, the matches can overlap,
as they do in this case.

Second, the `Match` record contains gives us information about where
the match was found. It also has a method `.endExclusive()`, which
returns the index right after the match.

Third, keywords can have multiple IDs. This is handy when you build
things like a gazetteer, which associates words with some
entities. For example, the word "Paris" can be associated with the
city of Paris. It can also be associated with the prince of Troy from
the Iliad. You can add both associations either together:

```java
multiSearch.register("Paris", CITY_PARIS, PERSON_PARIS);
```

or independently:

```java
multiSearch.register("Paris", CITY_PARIS);
// ...
multiSearch.register("Paris", PERSON_PARIS);
```

#### Partial results

The `.searchIn()` method returns the results as a Java Stream. This
allows you to stop the search at any point. For example, if we want to
check if the text contains _any_ of the keywords we could something
like this:

```java
boolean containsAny =
        finder.searchIn(text)
              .findAny()
              .isPresent();
```

#### Serialization

The `Finder` class can be serialized and stored in a file. For
example, we can prepare the gazetteer we mentioned before and save it
in a file. When we need to use it, we can just load that file.

See `MultiSearchTest.finderIsSerializableWhenIdIsSerializable()` for
an example.

#### Examples

The
[tests](src/test/java/io/github/itoshkov/multisearch/MultiSearchTest.java)
also provide simple usage examples.

### Beyond strings

The `StringMultiSearch` class is a wrapper around the more general
`MultiSearch` class. The latter can be used with sequences of any
type:

```java
// Sequence of bytes for an anti-virus scanner.
MultiSearch<Byte, VirusId> virusSignatures = MultiSearch.implementation();

// Register virus signatures

MultiSearch.Finder<Byte, Keyword> virusSignatureMatcher =
        virusSignatures.prepareFinder();
```

```java
// Sequence of words.
MultiSearch<String, Integer> wordMultiSearch =
        MultiSearch.implementation(Algorithm.AHO_CORASICK);
```

The `.searchIn()` method accepts an `Iterator` as parameter, though
there are a couple of convenience methods that work with `Collection`s
or arrays.

## Build

To build the library from source use:

```shell
./mvnw verify
```
