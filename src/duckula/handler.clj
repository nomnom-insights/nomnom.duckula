(ns duckula.handler
  "Default Duckula handler. It talks JSON
  but can validate requests with provided Avro schemas"
  (:require
    [clojure.string :as s]
    [clojure.tools.logging :as log]
    [duckula.avro]
    [duckula.protocol :as monitoring]))

(defn use-kebab-case? [{:keys [mangle-names? kebab-case-names?]}]
  (or mangle-names?
      kebab-case-names?))


(defn build-route-map
  "Turns static config (documented below) into a map of function maps:
  - request handler
  - request input validator
  - request output validator
  Validators use Avro to ensure passed in data is ok"
  [{:keys [prefix endpoints] :as config}]
  (let [mangle-names? (use-kebab-case? config)]
    (->> endpoints
         (map (fn route-builder [[path conf]]
                (hash-map (str prefix path)
                          (let [validator-opts  {:mangle-names? mangle-names?
                                                 :soft-validate? (:soft-validate? conf)}]
                            (-> conf
                                (update :request
                                        (fn request-validator [schema]
                                          (duckula.avro/validator schema validator-opts)))
                                (update :response
                                        (fn response-validator [schema]
                                          (duckula.avro/validator schema validator-opts))))))))
         (into {}))))



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
       (map (fn metric-key-builder [[path _conf]]
              (let [metric-key (str (:name config) (s/replace (str prefix path) \/ \.))
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
  {:body  {:message "not found"}
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


(defn build
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
  [config]
  (let [routes (build-route-map config)
        metrics (build-metric-keys config)]
    (fn wrapped-handler [{:keys [uri component headers body] :as request}]
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
                  (monitoring/on-success monitoring success-key {:body body :status status})
                  (monitoring/on-error monitoring error-key))
                response)
              (catch Exception err
                (monitoring/on-failure monitoring failure-key)
                (let [{:keys [validation-type] :as metadata} (ex-data err)
                      to-report (merge
                                  headers
                                  metadata
                                  (select-keys request [:uri :host :request-host]))]
                  (monitoring/track-exception monitoring  err to-report)
                  {:body {:message "Request failed"
                          :error (.getMessage err)
                          :metadata metadata}
                   :status (if (= ::request validation-type)
                             410 ; input failure
                             500) ; server failure
                   })))
            (do
              (monitoring/on-not-found monitoring error-key uri)
              (not-found-404))))))))
