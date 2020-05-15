(ns duckula.avro.schema-test
  (:require
    [clojure.test :refer [is deftest testing]]
    [duckula.avro]
    [duckula.avro.schema :as avro.schema]
    [schema.core]))


(def sample-avro-schema
  (duckula.avro/load-schemas "schema/endpoint/search/test/Response.avsc"))


(deftest generates-a-prismatic-schema-from-avro
  (let [payload {:status "error"
                 :items [{:content "foo" :id 1}
                         {:content "bar" :id 2}]
                 :message "foo"}]
    (is (= payload
           (schema.core/validate
            (avro.schema/->map sample-avro-schema)
            payload)))))
