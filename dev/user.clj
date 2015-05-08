(ns user
  (:require [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env] :rename {env sys-env}]

            [ifs-part-search.system :as sys]))

(def default-env
  {:db-host "neon"
   :db-name "IFST"})

(def system nil)

(defn init []
  (alter-var-root #'system (constantly (sys/system
                                         (merge default-env sys-env)))))

(defn start []
  (alter-var-root #'system sys/start))

(defn stop []
  (if system
    (alter-var-root #'system sys/stop)))

(defn url []
  (str "http://localhost:" (-> system :server :port)))

(defn go
  "Initialize the current development system and start it's components"
  []
  (init)
  (start)
  (str "running at " (url)))

(defn reset []
  (stop)
  (refresh :after 'user/go))
