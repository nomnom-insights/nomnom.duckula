(ns duckula.test.component.http-server
  (:require
   [com.stuartsierra.component :as component]
   [ring.component.jetty :as jetty]
   [ring.middleware.params :as params]
   [ring.middleware.json :as ring-json]
   [ring.middleware.defaults :as ring-defaults]))

(defn wrap-handler [handler-fn]
  (-> handler-fn
      (ring-json/wrap-json-body {:keywords? true})
      params/wrap-params
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)))

(defrecord Handler [handler-fn]
  component/Lifecycle
  (start [this]
    (let [handler (wrap-handler handler-fn)]
      (assoc this :handler (fn [req]
                             (handler (assoc req :component this))))))
  (stop [this] (assoc this :handler nil)))

(defn create [handler-fn dependencies conf]
  (let [handler-key (keyword (:name conf) "handler")
        server-key (keyword (:name conf) "server")]
    {handler-key (component/using (map->Handler {:handler-fn handler-fn})
                                  dependencies)
     server-key (component/using (jetty/jetty-server conf)
                                 {:app handler-key})}))
