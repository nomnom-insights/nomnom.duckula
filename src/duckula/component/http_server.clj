(ns duckula.component.http-server
  (:require
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty9 :as jetty])
  (:import
   org.eclipse.jetty.server.Server))

(defn- process-jetty-config
  "Verifies that at least we have a valid port and sets sensible defaults.
  Other supported options:
  https://github.com/sunng87/ring-jetty9-adapter/blob/5f81adbec333af29ed9802db8181e5e4ed8046c6/src/ring/adapter/jetty9.clj#L262"
  [{:keys [port] :as conf}]
  {:pre [(and (number? port) (>= port 2000))]}
  (merge
    ;; sensible defaults
   {:http? true
     ;; just in case, ensure these extra protocols are off (should be!)
    :http3? false
    :h2? false
    :h2c? false
    :host "0.0.0.0"
     ;; we need the server thread in fg
    :join? false
     ;; just in case, again - so that we don't run into issues with
     ;; ssl ciphers and JDK
    :ssl? false}
   conf))

(defrecord JettyServer [;; dependency, represents the ring handler
                        app
                        ;; jetty configuration, see: `process-jetty-config`
                        config
                        ;; internal state:
                        ;; stores the running instance
                        server
                        ;; stores the ring handler, unwrapped
                        handler]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [handler (:handler app)
            server (jetty/run-jetty handler config)]
        (assoc this
               :server server
               :handler handler))))
  (stop [this]
    (if-let [^Server server (:server this)]
      (do
        (.stop server)
        (.join server)
        (dissoc this :server :handler))
      this)))

(defrecord Handler [;; Required Ring handler function
                    request-handler-fn
                    ;; Internal state: represents the wrapped handler and injects
                    ;; dependencies into the request map
                    handler]
  component/Lifecycle
  (start [this]
    (assoc this :handler (fn [req]
                           (request-handler-fn (assoc req :component this)))))
  (stop [this] (assoc this :handler nil)))

(defn create-handler
  "Create Handler component, usually needs wrapping in (component/using ... [:deps])"
  [request-handler]
  (->Handler request-handler nil))

(defn create-server
  "Create Jetty server component, must have :app as dependency:
  `(component/using (create-server) { :app :api })`"
  [config]
  (map->JettyServer {:config (process-jetty-config config)}))

(defn create
  "Create a mini system composed of the http server with a Ring handler attached"
  [{:keys [handler config]}
   dependencies]
  (let [handler-key (keyword (:name config) "handler")
        server-key (keyword (:name config) "server")]
    {handler-key (component/using (create-handler handler)
                                  dependencies)
     server-key (component/using (create-server config)
                                 {:app handler-key})}))
