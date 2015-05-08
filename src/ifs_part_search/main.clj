(ns ifs-part-search.main
  (:require [ifs-part-search.system :as sys])
  (:gen-class))

(defn add-shutdown-hook [s]
  (.addShutdownHook
    (Runtime/getRuntime)
    (Thread. (fn []
               (println "stopping...")
               (sys/stop s)))))

(defn -main [& args]
  (let [s (sys/start (sys/system))]
    (add-shutdown-hook s)
    (println "running on port " (-> s :server :port))))
