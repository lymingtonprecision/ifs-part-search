(ns ifs-part-search.database
  (:require [com.stuartsierra.component :as component]
            [hikari-cp.core :as hk]))

(def default-options
  (merge
    hk/default-datasource-options
    {:auto-commit true
     :read-only false
     :minimum-idle 2
     :maximum-pool-size 10
     :adapter "oracle"
     :driver-type "thin"
     :port-number 1521}))

(def env-keys-to-option-names
  {:db-name :database-name
   :db-host :server-name
   :db-user :username
   :db-password :password})

(defn extract-options-from-env [env]
  (reduce
    (fn [opts [k v]]
      (if-let [k (env-keys-to-option-names k)]
        (assoc opts k v)
        opts))
    {}
    env))

(defn merge-options-with-env [env]
  (merge default-options (extract-options-from-env env)))

(defrecord Database [env]
  component/Lifecycle
  (start [this]
    (let [o (merge-options-with-env env)
          ds (hk/make-datasource o)]
      (assoc this :options o :datasource ds)))

  (stop [this]
    (if-let [ds (:datasource this)]
      (hk/close-datasource ds))
    (dissoc this :datasource)))

(defn database []
  (component/using
    (map->Database {})
    [:env]))
