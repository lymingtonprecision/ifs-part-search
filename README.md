# ifs-part-search

A HTTP search service for parts within IFS.

## Running

First, set the environment variables for establishing a database
connection:

* `DB_HOST` the database server to connect to.
* `DB_NAME` the database instance to use.
* `DB_USER` the user to connect as.
* `DB_PASSWORD` the password to authenticate with.

The `PORT` to bind to may also, optionally, be specified. If no port
is specified then an available port will be selected at random. The
URL of the service will be printed to the console after it starts.

These environment variables can either be set from system environment
variables, a `.lein-env` file, or Java system properties. See the
[environ](https://github.com/weavejester/environ) documentation for
more details.

To run the service from a `.jar`:

    java -jar ./path/to/ifs-part-search.jar

From `lein`:

    lein run

From the repl:

    lein repl
    user=> (go)

## Usage

Make a HTTP `GET` request to the `search` page with your search query
in the `q` URI parameter:

    ; <ifs-part-search>/search?q=<search string>
    localhost:8080/search?q=bias+unit

This will return a list of matching parts as
[JSON formatted Transit](http://transit-format.org/).
(Empty searches will not return any results.)

In addition to searching based on the specified terms (which can be
negated by prefixing with `-`) you can also specify one or more search
operators to further filter the results.

For example:

    ; search terms: bias unit -orbit planner:jelliott
    localhost:8080/search?q=bias%20unit%20-orbit%20planner%3Ajelliott

Will return any parts matching "bias" and "unit" but not "orbit"
assigned to the planner "jelliott".

Search operators can be specified multiple times, have multiple (comma
separated) values, and be negated.

See the [query parser documentation] for full details of the available
search syntax.

[query parser documentation]: ./tree/master/src/ifs_part_search/query_parser.clj

## Acknowledgements

[instaparse](https://github.com/Engelberg/instaparse) for making it so
effortless to compose a grammar and parser.

[google](http://www.google.com/) for the search syntax inspiration.

[Evernote](https://dev.evernote.com/doc/articles/search_grammar.php#Formal_Search_Grammar)
for publishing their formal search grammar in EBNF notation, giving me
a point of reference other than a blank slate.

## License

Copyright © 2015 Lymington Precision Engineers Co. Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
