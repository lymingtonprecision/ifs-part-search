(ns ifs-part-search.query-writer-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [schema.test]
            [clojure.string :as str]
            [ifs-part-search.query-parser :as qp]
            [ifs-part-search.query-writer :as qw]))

(use-fixtures :once schema.test/validate-schemas)

(deftest an-empty-query-yeilds-nil
  (is (nil? (qw/query->sql {:query nil :filters {}}))))

(deftest unfiltered-query
  (let [q (qp/search-str->query
           "bias unit 900")]
    (is (= [(str "SELECT"
                 " ip.part_no, ip.description"
                 " FROM ifsapp.inventory_part ip"
                 " WHERE contains(ip.text_id$, ?, 1) > 0"
                 " ORDER BY score(1) DESC, ip.description DESC")
            (:query q)]
           (qw/query->sql q)))))

(deftest planner-filter-values-are-upcased
  (let [p "jelliott"
        q (qp/search-str->query (str "bias planner:" p))]
    (is (= (str/upper-case p) (nth (qw/query->sql q) 2)))))

(deftest single-filter-value-query
  (let [p "JELLIOTT"
        q (qp/search-str->query
           (str "bias unit 900 planner:" p))]
    (is (= [(str "SELECT"
                 " ip.part_no, ip.description"
                 " FROM ifsapp.inventory_part ip"
                 " WHERE ("
                 "contains(ip.text_id$, ?, 1) > 0"
                 " AND "
                 "(ip.planner_buyer in (?))"
                 ") ORDER BY score(1) DESC, ip.description DESC")
            (:query q) p]
           (qw/query->sql q)))))

(deftest negated-filter-value-query
  (let [p "JELLIOTT"
        q (qp/search-str->query
           (str "bias unit 900 -planner:" p))]
    (is (= [(str "SELECT"
                 " ip.part_no, ip.description"
                 " FROM ifsapp.inventory_part ip"
                 " WHERE ("
                 "contains(ip.text_id$, ?, 1) > 0"
                 " AND "
                 "(ip.planner_buyer not in (?))"
                 ") ORDER BY score(1) DESC, ip.description DESC")
            (:query q) p]
           (qw/query->sql q)))))

(deftest multiple-filter-value-query
  (let [ps ["JELLIOTT" "SBENNETT"]
        np "MGIBSON"
        q (qp/search-str->query
           (str "bias unit 900 planner:" (str/join "," ps)
                " -planner:" np))]
    (is (= (flatten
            [(str "SELECT"
                  " ip.part_no, ip.description"
                  " FROM ifsapp.inventory_part ip"
                  " WHERE ("
                  "contains(ip.text_id$, ?, 1) > 0"
                  " AND "
                  "((ip.planner_buyer in (?, ?)) AND"
                  " (ip.planner_buyer not in (?)))"
                  ") ORDER BY score(1) DESC, ip.description DESC")
             (:query q) ps np])
           (qw/query->sql q)))))
