(ns duckula.swagger
  "Provides a Ring middleware which can serve swagger.json generated from the API config
  as well as Swagger UI to view it"
  (:require
    [cheshire.core :as json]
    [clojure.string :as string]
    [duckula.avro :as avro]
    [duckula.handler]

    ;; These should optional
    [duckula.avro.schema :as avro.schema]
    [ring.swagger.swagger-ui :as swagger.ui]
    [ring.swagger.swagger2 :as rs]))



(def error-schemas
  (let [error (with-meta
                {:message avro.schema/string
                 :error avro.schema/string
                 :metadata avro.schema/any-map}
                {:name "Error"})]
    {410 {:description "Request data didn't conform to the request data schema"
          :schema error}
     500 {:description "Internal server error, or response couldn't be serialized according to the response schema"
          :schema error}}))

(defn make-schema-name [req-schema-path]
  (string/replace req-schema-path "/" "."))


(defn make-definition [{:keys [type avro-schema-path path]}]
  (let [avro-schema (when avro-schema-path
                      (-> avro-schema-path avro/name->path avro/load-schemas))
        schema (with-meta
                 (if avro-schema
                   (avro.schema/->map avro-schema)
                   avro.schema/any)
                 {:name (make-schema-name (or avro-schema-path path))})
        description (if avro-schema
                      (.getDoc avro-schema)
                      ":no-doc:")]
    {:schema schema :description description}))


(defn endpoint->swagger [path config]
  (let [{:keys [request response]} config
        request-config (make-definition {:avro-schema-path request
                                         :path path})
        response-config (make-definition {:avro-schema-path response
                                          :path path})]
    {path {:post {:summary path
                  :description (or (:description request-config) "")
                  :parameters {:body (:schema request-config)}
                  :responses (merge error-schemas
                                    {200 {:description (:description response-config)
                                          :schema (:schema response-config)}})}}}))


(defn config->swagger [{:keys [name prefix endpoints] :as _config}]
  {:swagger "2.0"
   :info {:title (str "Swagger API: " name)
          :version "0.0.1"}
   :produces ["application/json"]
   :consumes ["application/json"]
   :definitions {}
   :paths (->> endpoints
               (map
                (fn [[path config]] (endpoint->swagger (str prefix path) config)))
               (into {}))})


(defn generate [config]
  (-> config
      config->swagger
      rs/swagger-json))


(defn build-handler
  "Returns a Ring handler which returns auto-generated
  swagger json, based on the Duckula config"
  [config]
  (let [swagger-json (generate config)]
    (fn swagger-json-handler [_req]
      {:status 200
       :body (json/generate-string swagger-json)
       :headers {"content-type" "application/json"}})))


(def swagger-ui-handler swagger.ui/swagger-ui)


(def ui-prefix "/~docs/ui")
(def swagger-json-path "/~docs/swagger.json")


(defn with-docs [api-config]
  (let [ui-handler (swagger-ui-handler {:path ui-prefix
                                        :swagger-docs swagger-json-path})
        swagger-json-handler (build-handler api-config)
        api-handler (duckula.handler/build api-config)]

    (fn [{:keys [uri] :as req}]
      (cond
        (string/starts-with? uri ui-prefix) (ui-handler req)
        (= uri swagger-json-path) (swagger-json-handler req)
        :else (api-handler req)))))
