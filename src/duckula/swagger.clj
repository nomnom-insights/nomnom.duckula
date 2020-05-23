(ns duckula.swagger
  "Provides a Ring middleware which can serve swagger.json generated from the API config
  as well as Swagger UI to view it"
  (:require
    [cheshire.core :as json]
    [clojure.string :as string]
    [duckula.avro :as avro]
    [duckula.handler]))


(try

  (require '[duckula.avro.schema :as avro.schema]
           '[ring.swagger.swagger-ui :as swagger.ui]
           '[ring.swagger.swagger2 :as rs])
  (catch Exception _err))


(def dependencies-satisfied?
  (every?
    true?
    [(if (find-ns 'ring.swagger.swagger2)
       true
       (println "ring.swagger not found"))
     (if (find-ns 'ring.swagger.swagger-ui)
       true
       (println "ring.swagger.swagger-ui not found"))
     (if (find-ns 'schema.core)
       true
       (println "Plumatic Schema not found"))]))


(when dependencies-satisfied?
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


  (defn make-definition [response path]
              (let [response-avro-schema (when response
                                 (-> response avro/name->path avro/load-schemas))
          response-schema (with-meta
                            (if response-avro-schema
                              (avro.schema/->map response-avro-schema)
                             avro.schema/any)
                            {:name (make-schema-name (or response path))})]
  response-schema))

  (defn endpoint->swagger [path config]
    (let [{:keys [request response]} config
          request-avro-schema (when request
                                (-> request avro/name->path avro/load-schemas))
          request-schema (with-meta
                           (if request-avro-schema
                             (avro.schema/->map request-avro-schema)
                             avro.schema/any)
                           {:name (make-schema-name (or request path))})
          response-avro-schema (when response
                                 (-> response avro/name->path avro/load-schemas))
          response-schema (with-meta
                            (if response-avro-schema
                              (avro.schema/->map response-avro-schema)
                             avro.schema/any)
                            {:name (make-schema-name (or response path))})]
      {path {:post {:summary path
                    :description path
                    :parameters {;:description (if request-avro-schema (.getDoc request-avro-schema) ":no-doc:")
                                 :body request-schema}
                    :responses (merge error-schemas
                                      {200 {:description (if response-avro-schema
                                                           (.getDoc response-avro-schema)
                                                           ":no-doc:")
                                            :schema response-schema}})}}}))


  (defn config->swagger [{:keys [name endpoints] :as _config}]
    {:swagger "2.0"
     :info {:title (str "Swagger API: " name)
            :version "0.0.1"}
     :produces ["application/json"]
     :consumes ["application/json"]
     :definitions {}
     :paths (->> endpoints
                 (map
                   (fn [[path config]] (endpoint->swagger path config)))
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
          :else (api-handler req))))))
