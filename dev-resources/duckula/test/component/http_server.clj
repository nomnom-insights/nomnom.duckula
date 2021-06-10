(ns duckula.test.component.http-server
  (:require
    [com.stuartsierra.component :as component]
    [ring.component.jetty :as jetty]))


(defrecord Handler
  [handler-fn]

  component/Lifecycle
  (start
    [this]
    (let [handler (:handler-fn this)]
      (assoc this :handler (fn [req]
                             (handler (assoc req :component this))))))


  (stop [this] (assoc this :handler nil)))


(defn create-handler
  [handler-fn]
  (map->Handler
    {:handler-fn handler-fn}))


(defn create-server
  [conf]
  (jetty/jetty-server conf))


(defn create
  [handler-fn dependencies conf]
  (let [handler-key (keyword (:name conf) "handler")
        server-key (keyword (:name conf) "server")]
    {handler-key (component/using (create-handler handler-fn)
                                  dependencies)
     server-key (component/using (create-server conf)
                                 {:app handler-key})}))
