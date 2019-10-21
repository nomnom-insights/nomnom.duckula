(ns duckula.test.server
  "Test HTTP server"
  (:require [duckula.test.component.http-server :as http-server]
            duckula.handler
            [duckula.component.basic-monitoring :as monitoring]
            [duckula.test.handler.echo :as handler.echo]
            [duckula.test.handler.number :as handler.number]
            [duckula.test.handler.search :as handler.search]
            [com.stuartsierra.component :as component]))

(def server (atom nil))

(def config
  {:name "test-server-rpc"
   :mangle-names? false
   :endpoints {"/search/test" {:request "search/test/Request"
                               :response "search/test/Response"
                               :handler handler.search/handler}
               "/number/multiply" {:request "number/multiply/Request"
                                   :response "number/multiply/Response"
                                   :handler handler.number/handler}
               "/number/multiply-soft" {:request "number/multiply/Request"
                                        ;; report validation errors but don't fail requests
                                        :soft-validate? true
                                        :response "number/multiply/Response"
                                        :handler handler.number/handler}
               ;; no validation
               "/echo" {:handler handler.echo/handler}}})

(defn start-with-handler! [handler]
  (let [sys (component/map->SystemMap
             (merge
              {:monitoring monitoring/BasicMonitoring}
              (http-server/create handler
                                  [:monitoring]
                                  {:name "test-rpc-server"
                                   :port 3003})))]
    (reset! server (component/start sys))))

(defn start! []
  (start-with-handler! (duckula.handler/build config)))

(defn stop! []
  (swap! server component/stop))
