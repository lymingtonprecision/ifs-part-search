(ns ifs-part-search.query-writer-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [schema.test]
            [clojure.string :as str]
            [ifs-part-search.query-parser :as qp]
            [ifs-part-search.query-writer :as qw]))

(use-fixtures :once schema.test/validate-schemas)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers to create the expected return values

(def select-fields
  (str "SELECT "
       (str/join ", " ["ip.part_no AS id"
                       "ipcp.cust_part_no AS customer_part"
                       "ipcp.issue"
                       "ipcp.description"
                       "ip.description AS full_description"
                       (str "decode(ip.type_code_db,"
                            " 3, 'Raw',"
                            " ip.type_code"
                            ") AS type")
                       "ip.part_status AS status_code"
                       "initcap(ps.description) AS status"])))
(def from-tables
  (str "FROM ifsapp.inventory_part ip"
       " INNER JOIN ifsinfo.inv_part_cust_part_no ipcp"
       " ON ip.part_no = ipcp.part_no"
       " INNER JOIN ifsapp.inventory_part_status_par ps"
       " ON ip.part_status = ps.part_status"))
(def where-contains "contains(ip.text_id$, ?, 1) > 0")
(def order-by "ORDER BY score(1) DESC, ip.description DESC")

(defn where-planner
  "Returns a `where` clause for `n` planner values (default `1`)
  which can optionally be `negative?` (i.e. a `not` clause.)

      (where-planner)        ;=> \"(ip.planner_buyer in (?))\"
      (where-planner 3)      ;=> \"(ip.planner_buyer in (?, ?, ?))\"
      (where-planner 1 true) ;=> \"(ip.planner_buyer not in (?))\"
  "
  ([] (where-planner 1))
  ([n] (where-planner n false))
  ([n negative?]
   (str "(ip.planner_buyer "
        (if negative? "not ")
        "in ("
        (str/join ", " (repeat n "?"))
        "))")))

(defn expected-stmt
  "Constructs the expected return value for `query->sql` tests.
  `where-clauses` should be a collection of additional `where` clauses
  and `params` the collection of expected parameter values.

      (expected-stmt [(:query q)])
      ;=> [\"SELECT ...\" <query value>]
      (expected-stmt [(where-planner)] [(:query q) planner])
      ;=> [\"SELECT ... WHERE ... (ip.planner_buyer ...) ...\"
      ;=>  <query value> planner]
  "
  ([params] (expected-stmt nil params))
  ([where-clauses params]
   (let [where (if (seq where-clauses)
                 (str "("
                      (str/join
                       " AND "
                       (cons where-contains (vec where-clauses)))
                      ")")
                 where-contains)
         sql (str/join
              " "
              [select-fields
               from-tables
               "WHERE" where
               order-by])]
     (cons sql (vec params)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; query->sql

(deftest an-empty-query-yeilds-nil
  (is (nil? (qw/query->sql {:query nil :filters {}}))))

(deftest unfiltered-query
  (let [q (qp/search-str->query
           "bias unit 900")]
    (is (= (expected-stmt [(:query q)]) (qw/query->sql q)))))

(deftest planner-filter-values-are-upcased
  (let [p "jelliott"
        q (qp/search-str->query (str "bias planner:" p))]
    (is (= (str/upper-case p) (nth (qw/query->sql q) 2)))))

(deftest single-filter-value-query
  (let [p "JELLIOTT"
        q (qp/search-str->query
           (str "bias unit 900 planner:" p))]
    (is (= (expected-stmt [(where-planner)] [(:query q) p])
           (qw/query->sql q)))))

(deftest negated-filter-value-query
  (let [p "JELLIOTT"
        q (qp/search-str->query
           (str "bias unit 900 -planner:" p))]
    (is (= (expected-stmt [(where-planner 1 true)] [(:query q) p])
           (qw/query->sql q)))))

(deftest multiple-filter-value-query
  (let [ps ["JELLIOTT" "SBENNETT"]
        np "MGIBSON"
        q (qp/search-str->query
           (str "bias unit 900 planner:" (str/join "," ps)
                " -planner:" np))]
    (is (= (expected-stmt [(str "(" (where-planner 2) " AND "
                                (where-planner 1 true) ")")]
                          (cons (:query q) (conj ps np)))
           (qw/query->sql q)))))
