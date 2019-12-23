(ns duckula.handler
  "Default Duckula handler. It talks JSON
  but can validate requests with provided Avro schemas"
  (:require [cheshire.core :as json]
            duckula.avro
            [duckula.protocol :as monitoring]
            [clojure.string :as s]
            [clojure.tools.logging :as log]))

(defn build-route-map
  "Turns static config (documented below) into a map of function maps:
  - request handler
  - request input validator
  - request output validator
  Validators use Avro to ensure passed in data is ok"
  [{:keys [prefix endpoints mangle-names?]}]
  (->> endpoints
       (map (fn [[path conf]]
              (hash-map (str prefix path)
                        (-> conf
                            (update :request #(duckula.avro/validator % {:mangle-names? mangle-names?
                                                                         :soft-validate? (:soft-validate? conf)}))
                            (update :response #(duckula.avro/validator % {:mangle-names? mangle-names?
                                                                          :soft-validate? (:soft-validate? conf)}))))))
       (into {})))

(defn build-metric-keys
  "For each endpoint it constructs a list of metric keys
  to be used when tracking timing, rates and errors
  Example for name test-api and /some/endpoint it will create
  test-api.some.endpoint
  test-api.some.endpoint.success
  test-api.some.endpoint.error
  test-api.some.endpoint.failure"
  [{:keys [endpoints prefix] :as config}]
  (->> endpoints
       (map (fn [[path _conf]]
              (let [metric-key (str (:name config) (s/replace path \/ \.))
                    success-key (str metric-key ".success")
                    error-key (str metric-key ".error")
                    failure-key (str metric-key ".failure")]
                (hash-map (str prefix path) [metric-key
                                             success-key
                                             error-key
                                             failure-key]))))
       (into {})))

(def not-found-metrics
  (let [k "api.not-found"]
    [k (str k ".success") (str k ".error") (str k ".failure")]))

(defn not-found-404 [& _]
  {:body (json/generate-string {:message "not found"})
   :headers {"content-type" "application/json"}
   :status 404})

(defn validate-with-tag
  "Runs validation function and re-throws the exception
  with extra info attached.
  Each generated avro validator function carries metadata with schema name"
  [tag validator-fn input monitoring]
  (let [{:keys [schema-name soft-validate?]} (meta validator-fn)]
    (try
      (validator-fn input)
      (catch Exception err
        (let [info {:schema-name schema-name
                    :soft-validate? soft-validate?
                    :validation-type tag}]
          (log/debugf err "invalid tag=%s schema=%s soft-validate=%s" tag schema-name soft-validate?)
          (if soft-validate?
            ;; report exception and pass through
            (do
              (monitoring/track-exception monitoring err)
              input)
            ;; otherwise re-throw
            (throw (ex-info (.getMessage err) info))))))))

(defn build [config]
  "Sort of a router, but does validation.
Config has to have the form of:
{
\"/some/route\" {:handler ring.rquest.handler/function
                 :response \"path/to/avro/response/Schema\"
                 :request \"path/to/avro/request/Schema\"}
; .. and more
\"/no/validation\" {:handler ring.req.handler/no-validation}
}
Handler assumes that requests use application/json for input and output as content types.
For 100% avro input/input we'd need a slightly different builder (and ring middlewares).
It depends on a component implementing  duckula.prococol/Monitoring protocol
- request count (success, error, failure)
- request timing
- track exceptions"
  (let [routes (build-route-map config)
        metrics (build-metric-keys config)]
    (fn [{:keys [uri component headers body] :as request}]
      (let [{:keys [monitoring]} component
            request-fns (get routes uri)
            request-validator (get request-fns :request)
            response-validator (get request-fns :response)
            handler-fn (get request-fns :handler)
            [metric-key success-key error-key failure-key] (get metrics uri not-found-metrics)]
        (monitoring/with-timing monitoring metric-key
          (if handler-fn
            (try
              (validate-with-tag ::request request-validator (or body {}) monitoring)
              (let [{:keys [status body] :as response} (handler-fn request)
                    ok? (< status 400)]
                (validate-with-tag ::response response-validator (or body {}) monitoring)
                (if ok?
                  (monitoring/on-success monitoring success-key {:body body :staus status})
                  (monitoring/on-error monitoring error-key))
                (-> response
                    (assoc-in [:headers "content-type"] "application/json")
                    (update :body json/generate-string)))
              (catch Exception err
                (monitoring/on-failure monitoring failure-key)
                (let [{:keys [validation-type] :as metadata} (ex-data err)
                      to-report (merge
                                 headers
                                 metadata
                                 (select-keys request [:uri :host :request-host]))]
                  (monitoring/track-exception monitoring  err to-report)
                  {:body (json/generate-string
                          {:message "Request failed"
                           :error (.getMessage err)
                           :metadata metadata})
                   :status (if (= ::request validation-type)
                             410 ; input failure
                             500) ; server failure
                   :headers {"content-type" "application/json"}})))
            (do
              (monitoring/on-not-found monitoring error-key uri)
              (not-found-404))))))))
