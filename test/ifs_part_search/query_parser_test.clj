(ns ifs-part-search.query-parser-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.test]
            [ifs-part-search.query-parser :as qp]))

(use-fixtures :once schema.test/validate-schemas)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sanitization/Escaping

(defn insert-randomly-into-str
  "Returns a copy of string `s` with values from `vs`
  inserted at random positions."
  [s vs]
  (reduce
   (fn [r v]
     (let [i (rand-int (count r))]
       (str (reduce str (take i r)) v (reduce str (drop i r)))))
   s
   vs))

(def -sanitized-chars (set "\"'()[]{},.*?_"))

(defspec search-term-sanitization
  (prop/for-all
   [s gen/string-alphanumeric
    sc (gen/vector (gen/elements -sanitized-chars))]
   (is (= s (qp/sanitize-search-term (insert-randomly-into-str s sc))))))

(def -special-chars (set "&=\\-;~|$!>"))

(defspec special-char-escaping
  (prop/for-all
   [s gen/string-alphanumeric
    sc (gen/vector (gen/elements -special-chars))]
   (let [s (insert-randomly-into-str s sc)
         exp (if (seq s)
               (reduce (fn [r c]
                         (if (contains? -special-chars c)
                           (str r "\\" c)
                           (str r c)))
                       ""
                       s)
               s)]
     (is (= exp (qp/escape-special-chars s))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Simplification

(def simplify #(seq (qp/simplify-parse-result (qp/parse-search-str %))))

(deftest filter-simplification
  (testing "one-filter-one-value"
    (is (= [{:planner ["sbennett"]}]
           (simplify "planner:sbennett"))))
  (testing "one-filter-multiple-values"
    (is (= [{:planner ["sbennett" "jelliott"]}]
           (simplify "planner:sbennett,jelliott")))
    (is (= [{:planner ["sbennett"]} {:planner ["jelliott"]}]
           (simplify "planner:sbennett planner:jelliott"))))
  (testing "one-negated-filter"
    (is (= [{:planner ['not ["sbennett"]]}]
           (simplify "-planner:sbennett")))
    (is (= [{:planner ['not ["sbennett" "jelliott"]]}]
           (simplify "-planner:sbennett,jelliott"))))
  (testing "one-filter-negative-and-positive"
    (is (= [{:planner ["jelliott"]} {:planner ['not ["sbennett"]]}]
           (simplify "planner:jelliott -planner:sbennett"))))
  (testing "empty-values-in-list"
    (is (= [{:planner ["jelliott" "sbennett"]}]
           (simplify "planner:jelliott,,,sbennett")))))

(defspec negated-term-simplification
  (prop/for-all
   [s (gen/not-empty
       (gen/fmap #(clojure.string/replace % #"\"" "") gen/string-ascii))]
   (is (= [(list 'not (str "{" (qp/sanitize-search-term s) "}"))]
          (simplify (str "-\"" s "\""))))))

(defspec literal-term-simplification
  (prop/for-all
   [s (gen/not-empty
       (gen/fmap #(clojure.string/replace % #"\"" "") gen/string-ascii))]
   (is (= [[(str "{" (qp/sanitize-search-term s) "}")]]
          (simplify (str "\"" s "\""))))))

(defspec non-literal-term-simplification
  (prop/for-all
   [s (gen/not-empty
       (gen/fmap #(clojure.string/replace % #"[\" \-]" "")
                 gen/string-ascii))]
   (is (= [[(str "{" (qp/sanitize-search-term s) "}")
            (str "%" (-> (qp/sanitize-search-term s)
                         qp/escape-special-chars) "%")]]
          (simplify s)))))

(deftest simplification-of-filter-with-space-after-value-separator
  (is (= [{:planner ["jelliott"]} ["{sbennett}" "%sbennett%"]]
         (simplify "planner:jelliott, sbennett"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; search-str->term-map

(defn term-map
  ([] (term-map {}))
  ([{:keys [terms negations filters]
     :or {terms [] negations [] filters {}}}]
   {:terms terms :negations negations :filters filters}))

(deftest empty-search-term-map
  (is (= (term-map) (qp/search-str->term-map ""))))

(deftest errant-spaces
  (let [clean-search "bias 900"
        terms (term-map {:terms [["{bias}" "%bias%"]
                                 ["{900}" "%900%"]]})]
    (testing "spaces-between-terms"
      (is (= terms (qp/search-str->term-map
                    (clojure.string/replace clean-search " " "  ")))))
    (testing "trailing-spaces-after-terms"
      (is (= terms (qp/search-str->term-map (str clean-search "  ")))))
    (testing "leading-spaces-before-terms"
      (is (= terms (qp/search-str->term-map (str "  " clean-search)))))))

(deftest literal-and-non-literal-search-term-map
  (is (= (term-map {:terms [["{bias}" "%bias%"]
                            ["{unit}"]
                            ["{900}" "%900%"]]})
         (qp/search-str->term-map "bias \"unit\" 900"))))

(deftest filter-concatenation-in-term-map
  (is (= (term-map {:filters {:planner ["sbennett"
                                        "sfernandez"
                                        "jelliott"
                                        ['not "mgibson"]]}})
         (qp/search-str->term-map
          "planner:sbennett planner:sfernandez,jelliott -planner:mgibson"))))

(deftest term-negation-in-term-map
  (is (= (term-map {:terms [["{bias}" "%bias%"]]
                    :negations ["{orbit}"]})
         (qp/search-str->term-map "bias -orbit"))))

(deftest full-term-map
  (is (= (term-map {:terms [["{bias}"] ["{assy}" "%assy%"]]
                    :negations ["{orbit}"]
                    :filters {:planner ["sbennett" "jelliott"
                                        ['not "mgibson"]]}})
         (qp/search-str->term-map
          "planner:sbennett,jelliott \"bias\" assy -orbit -planner:mgibson"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Oracle text query production

(deftest empty-negation
  (is (nil? (qp/negations->txt-query-not []))))

(deftest negated-terms-are-concatenated
  (is (= "NOT ({bias})" (qp/negations->txt-query-not ["{bias}"])))
  (is (= "NOT ({bias} | {unit} | {assy})"
         (qp/negations->txt-query-not ["{bias}" "{unit}" "{assy}"]))))

(deftest single-terms-produce-single-search-seq
  (is (= ["{bias}"] (qp/terms->txt-query-seqs [["{bias}"]] identity))))

(deftest multiple-terms-produce-near-and-and-searches
  (is (= ["NEAR(({bias}, {unit}, {assy}), 100, TRUE)"
          "{bias} AND {unit} AND {assy}"]
         (qp/terms->txt-query-seqs
          [["{bias}"] ["{unit}"] ["{assy}"]]
          identity))))

(deftest txt-query-seqs-produces-literal-and-wildcard-seqs-of-terms
  (is (= ["NEAR(({bias}, {unit}, {assy}), 100, TRUE)"
          "{bias} AND {unit} AND {assy}"
          "NEAR((%bias%, {unit}, %assy%), 100, TRUE)"
          "%bias% AND {unit} AND %assy%"]
         (qp/terms->txt-query-seqs
          [["{bias}" "%bias%"] ["{unit}"] ["{assy}" "%assy%"]]
          identity))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; search-str->query

(deftest empty-search-str->query-test
  (is (= {:query nil :filters {}} (qp/search-str->query ""))))

(deftest search-str->query-test
  (is (= {:query (str "<query>"
                      "<textquery lang=\"ENGLISH\" grammar=\"CONTEXT\">"
                      "<progression>"
                      "<seq>(NEAR(({bias}, {unit}, {900}), 100, TRUE) NOT ({orbit}))</seq>"
                      "<seq>({bias} AND {unit} AND {900} NOT ({orbit}))</seq>"
                      "<seq>(NEAR((%bias%, {unit}, %900%), 100, TRUE) NOT ({orbit}))</seq>"
                      "<seq>(%bias% AND {unit} AND %900% NOT ({orbit}))</seq>"
                      "</progression>"
                      "</textquery>"
                      "</query>")
          :filters {:planner ["sbennett"
                              ['not "mgibson"]
                              "jelliott"
                              "sfernandez"]}}
         (qp/search-str->query
          (str "planner:sbennett "
               "bias \"unit\" 900 -orbit "
               "-planner:mgibson planner:jelliott,sfernandez")))))
