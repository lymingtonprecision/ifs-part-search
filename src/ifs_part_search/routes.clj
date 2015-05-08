(ns ifs-part-search.routes
  (:require [plumbing.core :refer [defnk]]
            [schema.core :as s]
            [clojure.java.jdbc :as jdbc]
            [ifs-part-search.query-parser :as qp]
            [ifs-part-search.query-writer :as qw]))

(defnk $search$GET
  "Search for parts"
  {:responses {200 [s/Any]}}
  [[:request [:query-params q :- s/Str]]
   [:resources database]]
  (if-let [s (-> q
                 qp/search-str->query
                 qw/query->sql)]
    {:body (jdbc/query database s)}
    {:body []}))
