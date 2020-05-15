(ns duckula.swagger
  (:require
    [duckula.avro :as avro]
    [duckula.avro.schema :as avro.schema]
    [ring.swagger.swagger2 :as rs]))


(defn endpoint->swagger [path config]
  (let [{:keys [request response]} config
        req-sch (when request
                  (-> request
                      avro/name->path
                      avro/load-schemas))

        req-schema (if req-sch
                     (avro.schema/->map req-sch)
                     {})
        resp-sch (when response
                   (-> response
                       avro/name->path
                       avro/load-schemas))
        resp-schema (if resp-sch
                      (avro.schema/->map resp-sch)
                      {})]
    {path {:post {:summary path
                  :description path
                  :parameters {:body req-schema}
                  :response {200 {:schema resp-schema}}}}}))


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
