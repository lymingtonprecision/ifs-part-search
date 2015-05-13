(ns ifs-part-search.routes
  (:require [clojure.string :as str]
            [plumbing.core :refer [defnk]]
            [schema.core :as s]
            [clojure.java.jdbc :as jdbc]
            [ifs-part-search.query-parser :as qp]
            [ifs-part-search.query-writer :as qw]))

(def sane-column-key (comp str/lower-case #(str/replace % #"_" "-")))

(defnk $search$GET
  "Search for parts"
  {:responses {200 [s/Any]
               400 [s/Any]}}
  [[:request [:query-params q :- s/Str]]
   [:resources database]]
  (let [s (-> q qp/search-str->query)]
    (if (:error s)
      {:status 400 :body (select-keys s [:error])}
      (let [sql (-> s qw/query->sql)
            r (if sql
                (jdbc/query database s :identifiers sane-column-key)
                [])]
        {:body r}))))
