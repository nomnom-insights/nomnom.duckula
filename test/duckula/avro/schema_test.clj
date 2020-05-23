(ns duckula.avro.schema-test
  (:require
    [abracad.avro.codec :as avro.codec]
    [cheshire.core :as json]
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


(deftest converts-avro-to-primastic-schema
  (let [avro-schema (avro.codec/parse-schema*
                      (json/generate-string
                        {:name "Foo"
                         :doc "a foo!"
                         :type "record"
                         :fields [{:name "bar" :type "string" :doc "bah"}]}))
        expected-schema {:bar avro.schema/string}]
    (is (= expected-schema
           (avro.schema/->map avro-schema)))))
