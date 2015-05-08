(ns ifs-part-search.query-writer
  "Produces SQL queries from parsed search queries.

  There is a single public `fn`: `query->sql` which takes a `Query` map
  (see the `ifs-part-search.query-parser` namespace) and returns a
  corresponding collection of `[sql-string & params]` that can be used
  to retrieve the matching parts from and IFS database (by being used
  in a JDBC `query` call, for example.)"
  (:require [honeysql.core :as sql]
            [honeysql.helpers :as sql-h]
            [schema.core :as s]
            [ifs-part-search.query-parser :as qp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema

(s/defschema SelectStatement
  (s/both s/Str
          (s/pred #(re-find #"(?i)^select\s" %) 'select-statement)))

(s/defschema SqlQuery
  [(s/one SelectStatement 'sql) s/Str])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Filter processing

(defmulti filter-column "Maps filter names to SQL column names" identity)
(defmethod filter-column :planner [_] :ip.planner_buyer)

(defmulti filter-value-fmt
  "Given a `f`ilter name and a `v`alue returns the value formatted
  appropriately for use within that filter"
  (fn [f v] f))

(defmethod filter-value-fmt :default [_ v] v)
(defmethod filter-value-fmt :planner [_ v] (clojure.string/upper-case v))

(defn separate-filter-values
  "Returns the filter values `vs` grouped into positive, `:+`,
  and negative, `:-`, groups."
  [vs]
  (group-by
   (fn [v]
     (if (and (seq v) (= 'not (first v)))
       :-
       :+))
   vs))

(defn filter-conditions
  "Returns a collection of SQL conditions for filtering column `f`
  with values `vs`."
  [f vs]
  (let [vsg (separate-filter-values vs)
        pos (map #(filter-value-fmt f %) (:+ vsg))
        neg (map #(->> % second (filter-value-fmt f)) (:- vsg))
        c (filter-column f)]
    (cond-> []
      (seq pos) (conj [:in c pos])
      (seq neg) (conj [:not-in c neg]))))

(defn add-filters
  "Retruns the `sql` map with the filters specified in the map `fm`
  added."
  [sql fm]
  (let [fvs (reduce
             (fn [r f]
               (into r (apply filter-conditions f)))
             []
             fm)]
    (if (seq fvs)
      (apply sql-h/merge-where sql fvs)
      sql)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn query->sql :- (s/maybe SqlQuery)
  "Returns a collection of the `[sql-string & params]` corresponding to
  a parsed `Query`."
  [q :- qp/Query]
  (if (:query q)
    (sql/format
     (add-filters
      {:select [:ip.part_no :ip.description]
       :from [[:ifsapp.inventory_part :ip]]
       :where [:> (sql/call :contains :ip.text_id$ (:query q) 1) 0]
       :order-by [[(sql/call :score 1) :desc] [:ip.description :desc]]}
      (:filters q)))
    nil))
