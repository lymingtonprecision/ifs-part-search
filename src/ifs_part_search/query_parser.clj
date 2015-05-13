(ns ifs-part-search.query-parser
  "Parses search strings into Oracle text queries and associated filter
  values.

      bias \"orbit 900\" -stage planner:jelliott

  ... searches for parts with both \"bias\" and \"orbit 900\" somewhere
  in the description, that don't contain \"stage\" in the description,
  and which are assigned to the planner \"jelliott\".

  A single `fn` `search-str->query` is provided to parse a search string
  and return a query structure.

  ## Search String Format

  The supported search string format is similar to that of Google and
  most search engines:

  * Space separated words are treated as non-ordered, non-literal,
    search terms (e.g. \"ass torq\" would match \"torquer assembly\")
  * Terms inside quotes are treated literally (e.g. \"\\\"torq\\\"\"
    would match \"torq wrench\" (sic) but not \"torquer\")
  * Terms can be made negative by prefixing with a hypen
    (e.g. \"assembly -torquer\" would match all non-torquer assembly
    parts) **but** negative terms are always treated literally
    (e.g. \"-torq\" only matches \"torq\" not \"torquer\")

  ### Search String Santization

  The following characters are removed from search terms: `\"'()[]{},.*?_`

  Search terms may contain the characters `&=\\-;~|$!>` but they have special
  meaning within Oracle text queries and will be escaped.

  ## Search Operators

  As with advanced Google searches, specific search operators can be
  used to filter the results based on other associated data. Currently
  there is only one such supported operator:

  * `planner:` restricts the search to only those parts under the
    control of the specified planner (e.g. \"bias unit
    planner:jelliott\" will return only J. Elliotts Bias Unit parts
    and not those assigned to any other planner.) The specified value
    must match the IFS planner code _exactly_.

  Search operators can take multiple values either by specifying the
  same operator multiple times (`planner:jelliott planner:sbennett`)
  or by separating the values with commas (`planner:jelliott,sbennett`.)

  You can also set negative values for the search operators by prefixing
  the operator with a minus (`-planner:mgibson`.)

  Note: any unrecognized search operators will be treated as search
  terms (e.g. \"unknown:operator\" tries to search for
  \"unknown:operator\") and there must be no space between the operator
  name and value (e.g. \"planner: jelliott\" will result in a search for
  \"planner:\" and \"jelliott\" rather than filtering the search to
  J. Elliotts parts.)

  ## Result Ranking

  The produced Oracle text queries are given in the [relaxtion template]
  format using progressively more permissive combinations of search
  terms in both `NEAR` and `AND` combinations (with `NEAR` matches being
  preferred over `AND`s.)

  Parts that match all search terms _exactly_ will be ranked highest,
  then those that match all but the last search term exactly (with a
  wildcard match on the last term), then those that match all but the
  last two terms, etc.

  [relaxation template]: http://docs.oracle.com/cd/B19306_01/text.102/b14218/csql.htm#sthref134"
  (:require [clojure.string :as str]
            [instaparse.core :as insta]
            [clojure.math.combinatorics :as combo]
            [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema

(s/defschema NonEmptyString
  (s/both s/Str (s/pred not-empty 'not-empty)))

(s/defschema NegatedValue
  [(s/one (s/eq 'not) 'not) (s/one NonEmptyString 'value)])

(s/defschema FilterValue
  (s/either NonEmptyString NegatedValue))

(s/defschema EmptyQuery
  {:query (s/eq nil)
   :filters (s/eq {})})

(s/defschema RealisedQuery
  {:query s/Str
   :filters {s/Keyword [FilterValue]}})

(s/defschema Query
  (s/either EmptyQuery RealisedQuery))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Grammer/Parser

(def parser
  "A parser for our supported search grammer"
  (insta/parser
   "<search> = <separator*> (term | filter) {<separator+> (term | filter)} <separator*>
    separator = ' '
    negator = <'-'>

    <term> = negated-term | positive-term
    negated-term = <negator> positive-term
    <positive-term> = literal-term | non-literal-term
    literal-term = <'\"'> #'[^\"]+' <'\"'>
    non-literal-term = #'[^\" -]+'

    filter = negator? filter-name <filter-separator> filter-value-list
    filter-separator = ':'
    <filter-name> = 'planner'
    <filter-value-separator> = ','
    <filter-value> = #'[A-Za-z]+'
    <filter-value-list> = filter-value {<filter-value-separator> filter-value?}"))

(defn parse-search-str
  "Parses a search string, returns the resulting parse tree."
  [q]
  (if (str/blank? q)
    []
    (parser q :partial false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parse tree transformation/formatting

(defn sanitize-search-term
  "Removes unsupported characters from search terms."
  [t]
  (str/replace t #"[\"'()\[\]{},.*?_]+" ""))

(defn escape-special-chars
  "Escapes special characters within search terms."
  [t]
  (str/replace t #"([&=\\\-;~|$!>])" "\\\\$1"))

(defn simplify-parse-result
  "Transforms a raw search query parse tree into a simpler
  representation, sanitizing and escaping the term values where
  appropriate.

  `:filter` nodes are replaced with maps of `{filter-name
  filter-value}`. The `filter-name`s are also converted to keywords.

  `:negated-term`s are replaced with variants of `['not term]` (where
  `'not` is a symbol and `term` is the negated search term.)

  `:literal-term`s are replaced with a single element collection of
  `[\"{term}\"]`.

  `:non-literal-term`s are replaced with two element collections where
  the first element is the literal search term and the second is a
  wildcard equivilant (`[\"{term}\" \"%term%\"]`)."
  [pt]
  (insta/transform
   {:negator (constantly 'not)
    :filter (fn [n? k-or-v & vs]
              (if (= 'not n?)
                {(keyword k-or-v) [n? (vec vs)]}
                {(keyword n?) (into [k-or-v] vs)}))
    :negated-term (fn [[l _]] (list 'not l))
    :literal-term (fn [v] [(str "{" (sanitize-search-term v) "}")])
    :non-literal-term (fn [v]
                        (let [v (sanitize-search-term v)]
                          [(str "{" v "}")
                           (str "%" (escape-special-chars v) "%")]))}
   pt))

(defn add-to-filters
  "Retruns the term map `tm` with filter values `vs` added to the
  filter entry `k`.

  If the filter being added is negative each value is added to the
  filter as `['not value]` otherwise the values are added as is."
  [tm [k vs]]
  (let [vs (if (= 'not (first vs))
             (map (fn [v] ['not v]) (second vs))
             vs)]
    (update-in tm [:filters k] #(into (vec %1) %2) vs)))

(defn map-terms-and-filters
  "Returns a map of the `:terms`, `:negations`, and `:filters` from a
  simplified parse result tree (see `simplify-parse-result`.)

  All of the search terms from the parse tree are added to a `:terms`
  collection, in the order in which they appear in the original
  search.

  Any negated terms are added to a `:negations` collection.

  The filters are added to a `:filters` map, if multiple values have
  been specified for the same filter then the last encountered value
  will be returned."
  [spr]
  (reduce
   (fn [r e]
     (cond
       (map? e) (apply add-to-filters r (vec e))
       (= 'not (first e)) (update-in r [:negations] conj (second e))
       :else (update-in r [:terms] conj e)))
   {:terms [] :negations [] :filters {}}
   spr))

(defn search-str->term-map
  "Returns a map of search terms and filters for the search string `q`."
  [q]
  (-> (parse-search-str q)
      simplify-parse-result
      map-terms-and-filters))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; fns to produce an Oracle text query from the transformed parse tree

(defn negations->txt-query-not
  "Returns an Oracle text query `NOT` string for excluding all of the
  search negations `ns` from a search."
  [ns]
  (if (seq ns)
    (str "NOT (" (reduce (fn [r t] (str r " | " t)) ns) ")")))

(defn term-combinations
  "Returns a collection of all possible combinations of the terms in
  `ts`, in order of precedence from most specific (literal) to least."
  [ts]
  (apply combo/cartesian-product ts))

(defn terms->txt-query-seqs
  "Returns a collection of Oracle text query search strings for all
  combinations of the terms `ts` using `NEAR` and `AND` searches.

  A `fmt-fn` can be provided to add additional formatting to the
  returned strings. It will be called once for each search string
  with the string as its only argument.

  The default `fmt-fn` is `#(str \"<seq>\" % \"</seq>\")`, if you
  provide your own `fmt-fn` it will need to add the `<seq>` tags
  to the search string (if required)--they do not form part of
  the search string passed to the `fmt-fn`."
  ([ts] (terms->txt-query-seqs ts #(str "<seq>" % "</seq>")))
  ([ts fmt-fn]
   (reduce
    (fn [r t]
      (cond-> r
        (> (count t) 1) (conj (fmt-fn (str "NEAR((" (str/join ", " t) "),"
                                           " 100, TRUE)")))
        true (conj (fmt-fn (str/join " AND " t)))))
    []
    (term-combinations ts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn search-str->query :- Query
  "Returns a map containing the Oracle text `:query` [relaxation
  template] for the search string `q` and any `:filters` also contained
  within the search string.

  The returned map has the fields:

  * `:query` the Oracle text query string corresponding to the terms in
    the given search string.
  * `:filter` a map of filter keys to values of any filters specified in
    the search string.

  [relaxation template]: http://docs.oracle.com/cd/B19306_01/text.102/b14218/csql.htm#sthref134"
  [q :- s/Str]
  (if (str/blank? q)
    {:query nil :filters {}}
   (let [s (search-str->term-map q)
         n (negations->txt-query-not (:negations s))
         ts (terms->txt-query-seqs
             (:terms s)
             #(str "<seq>(" % " " n ")</seq>"))
         tq (str "<query><textquery lang=\"ENGLISH\" grammar=\"CONTEXT\">"
                 "<progression>" (str/join ts) "</progression>"
                 "</textquery></query>")]
     {:query tq :filters (:filters s)})))
