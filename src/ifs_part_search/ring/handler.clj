(ns ifs-part-search.ring.handler
  (:require [com.stuartsierra.component :as component]
            [plumbing.core :refer [keywordize-map]]

            [fnhouse.handlers :as handlers]
            [fnhouse.routes :as routes]

            [ring.middleware.params :as params]
            [ring.middleware.transit :as mwtr]
            [ring.middleware.cors :as cors]

            [ifs-part-search.routes]))

(defn wrap-keywordize-params [handler]
  (fn [req]
    (handler (update-in req [:query-params] keywordize-map))))

(defn ring-middleware [handler]
  (-> handler
      (cors/wrap-cors identity)
      wrap-keywordize-params
      mwtr/wrap-transit-body
      params/wrap-params
      (mwtr/wrap-transit-response {:encoding :json})))

(defrecord RingHandler [database]
  component/Lifecycle
  (start [this]
    (let [h-fns (handlers/ns->handler-fns
                  'ifs-part-search.routes
                  (constantly nil))
          ch ((handlers/curry-resources h-fns) this)]
      (assoc this :f (ring-middleware (routes/root-handler ch)))))
  (stop [this]
    (dissoc this :f)))

(defn ring-handler []
  (component/using
    (map->RingHandler {})
    [:database]))
