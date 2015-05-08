(ns ifs-part-search.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer (env)]

            [ifs-part-search.logging :as log]
            [ifs-part-search.database :as db]
            [ifs-part-search.ring.handler :as h]
            [ifs-part-search.server :as srv]))

(defn system
  ([] (system env))
  ([env]
   (component/system-map
     :env env
     :database (db/database)
     :handler (h/ring-handler)
     :server (srv/server))))

(defn start [s]
  (log/start!)
  (component/start s))

(defn stop [s]
  (log/stop!)
  (component/stop s))
