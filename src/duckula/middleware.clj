(ns duckula.middleware
  "Minimal middleware to allow JSON based request handling"
  (:require
    [duckula.component.basic-monitoring :as monitoring]
    [ring.middleware.defaults :as ring-defaults]
    [ring.middleware.json :as ring-json]))


(defn wrap-handler
  "Wraps the ring request handler (most likely one built by duckula.handler
  and adds required JSON handling middlewares"
  [handler-fn]
  (->
   ; ring-json/wrap-json-response
      handler-fn
      (ring-json/wrap-json-body {:keywords? true})
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)))


(defn with-monitoring
  "Simple handler, which makes it easy to use Duckula without Components"
  ([handler]
   (fn [req]
     (handler (update-in req [:component] merge {:monitoring monitoring/basic}))))
  ([handler monitoring-impl]
   (fn [req]
     (handler (update-in req [:component] merge {:monitoring monitoring-impl})))))
