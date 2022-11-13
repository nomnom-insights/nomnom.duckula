(ns duckula.avro.schema-test
  (:require
   [abracad.avro.codec :as avro.codec]
   [cheshire.core :as json]
   [clojure.test :refer [is deftest testing]]
   [duckula.avro]
   [duckula.avro.schema :as avro.schema]
   [schema.core :as s]))

(def sample-avro-schema
  (duckula.avro/load-schemas "schema/endpoint/search/test/Response.avsc"))

(defn build-avro-schema [shape]
  (avro.codec/parse-schema*
   (json/generate-string
    shape)))

(deftest generates-a-prismatic-schema-from-avro
  (let [payload {:status "error"
                 :items [{:content "foo" :id 1}
                         {:content "bar" :id 2}]
                 :message "foo"}]
    (is (= payload
           (s/validate
            (avro.schema/->map {:avro-schema sample-avro-schema})
            payload)))))

(deftest converts-avro-to-primastic-schema
  (testing "simple case"
    (let [avro-schema (build-avro-schema
                       {:name "Foo"
                        :doc "a foo!"
                        :type "record"
                        :fields [{:name "foo_bar" :type "string" :doc "bah"}]})
          expected-schema {:foo_bar avro.schema/string}]
      (is (= expected-schema
             (avro.schema/->map {:avro-schema avro-schema})))))

  (testing "name mangling :-("
    (let [avro-schema (build-avro-schema
                       {:name "Foo"
                        :doc "a foo!"
                        :type "record"
                        :fields [{:name "foo_bar" :type "string" :doc "bah"}]})
          expected-schema {:foo-bar avro.schema/string}]
      (is (= expected-schema
             (avro.schema/->map {:avro-schema avro-schema :mangle-names? true})))))

  (testing "enums accept both strings and keywords to match abracad"
    (let [avro-schema (build-avro-schema {:name "foo"
                                          :type "record"
                                          :fields [{:name "bar"
                                                    :type {:type "enum"
                                                           :name "some.Enum"
                                                           :symbols ["one" "two"]}}]})

          expected-schema {:bar (s/enum "one" "two" :one :two)}]
      (is (= expected-schema (avro.schema/->map {:avro-schema avro-schema})))))
  (testing "union type without optional fields"
    (let [avro-schema (build-avro-schema {:name "Test"
                                          :type "record"
                                          :fields [{:name "bar"
                                                    :type ["long" "string"]}]})]
      (is (= {:bar (s/cond-pre s/Int avro.schema/string)}
             (avro.schema/->map {:avro-schema avro-schema})))))
  (testing "map type accepts both string and keywords for keys"
    (let [avro-schema (build-avro-schema {:name "Test"
                                          :type "record"
                                          :fields [{:name "foo"
                                                    :type {:type "map" :values "string"}}]})]
      (is (= {:foo {:ok "bar"}}
             (s/validate (avro.schema/->map {:avro-schema avro-schema}) {:foo {:ok "bar"}})))))
  (testing "union type with optional fields"
    (let [avro-schema (build-avro-schema {:name "Test"
                                          :type "record"
                                          :fields [{:name "bar"
                                                    :type ["null" "string"]}]})]
      (is (= {(s/optional-key :bar) (s/cond-pre avro.schema/null-val avro.schema/string)}
             (avro.schema/->map {:avro-schema avro-schema}))))))
