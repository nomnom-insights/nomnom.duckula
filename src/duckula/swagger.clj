(ns duckula.swagger
  (:require
    [cheshire.core :as json]
    [clojure.string :as string]
    [duckula.avro :as avro]
    [duckula.avro.schema :as avro.schema]
    [duckula.handler]
    [ring.swagger.swagger-ui :as swagger.ui]
    [ring.swagger.swagger2 :as rs]))


(defn make-schema-name [req-schema-path]
  (string/replace req-schema-path "/" "."))


(defn endpoint->swagger [path config]
  (let [{:keys [request response]} config
        req-sch (when request
                  (-> request
                      avro/name->path
                      avro/load-schemas))

        req-schema (if req-sch
                     (with-meta
                       (avro.schema/->map req-sch)
                       {:name (make-schema-name request)})
                     {})
        resp-sch (when response
                   (-> response
                       avro/name->path
                       avro/load-schemas))
        resp-schema (if resp-sch
                      (with-meta
                        (avro.schema/->map resp-sch)
                        {:name (make-schema-name response)})
                      {})]
    {path {:post {:summary path
                  :description path
                  :parameters {:body req-schema}
                  :responses {200 {:schema resp-schema}}}}}))


(defn config->swagger [config]
  {:swagger "2.0"
   :info {:title "Swagger API"
          :version "0.0.1"}
   :produces ["application/json"]
   :consumes ["application/json"]
   :definitions {}
   :paths (->> config
               :endpoints
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


(defn with-docs [api-config]
  (let [ui-handler (swagger-ui-handler {:path "/~docs/ui"
                                        :swagger-docs "/~docs/swagger.json"})
        swagger-json-handler (build-handler api-config)
        api-handler (duckula.handler/build api-config)]

    (fn [{:keys [uri] :as req}]
      (println uri)
        (cond
          (string/starts-with? uri "/~docs/ui") (ui-handler req)
          (= uri "/~docs/swagger.json") (swagger-json-handler req)
          :else (api-handler req)))))
