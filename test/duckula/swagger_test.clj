(ns duckula.swagger-test
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]]
    [duckula.swagger :as swag]
    [duckula.test.server :as test.server]))


(deftest it-generates-a-swagger-config
  (testing "empty endpoints"
    (let [conf (swag/generate {:name "empty"} #_ test.server/config)]
      (is (= {:consumes ["application/json"]
              :definitions {}
              :info {:title "Swagger API: empty" :version "0.0.1"}
              :paths {}
              :produces ["application/json"]
              :swagger "2.0"}
             conf))))
  (testing "endpoint with no validations"
    (let [conf (swag/generate {:name "no validation"
                               :prefix "/SOAP"
                               :endpoints {"/foo" {:handler identity}}})]
      (is (= {:consumes ["application/json"]
              :definitions {"Error" {:additionalProperties false
                                     :properties {:error {:type "string"}
                                                  :message {:type "string"}
                                                  :metadata {:$ref "#/definitions/ErrorMetadata"}}
                                     :required [:message :error :metadata]
                                     :type "object"}
                            "ErrorMetadata" {:additionalProperties {}, :type "object"}}
              :info {:title "Swagger API: no validation", :version "0.0.1"}
              :paths {"/SOAP/foo" {:post {:description ":no-doc:"
                                     :parameters [{:description ""
                                                   :in "body"
                                                   :name "/SOAP/foo"
                                                   :required true
                                                   :schema {}}]
                                     :responses {200 {:description ":no-doc:"
                                                      :schema {}}
                                                 410 {:description "Request data didn't conform to the request data schema"
                                                      :schema {:$ref "#/definitions/Error"}}
                                                 500 {:description "Internal server error, or response couldn't be serialized according to the response schema"
                                                      :schema {:$ref "#/definitions/Error"}}}
                                     :summary "/SOAP/foo"}}}
              :produces ["application/json"]
              :swagger "2.0"}
             conf)))))


(deftest working-server-example-config
  (let [conf (swag/generate test.server/config)]
    (is (= (edn/read-string (slurp (io/resource "duckula/test_swagger.edn")))
           conf))))


(deftest with-inline-schema
  (let [conf (swag/generate {:name "empty"
                             :endpoints {
                                         "/test" { :request {:type "record"
                                                             :name "test.Empty"
                                                             :fields [ {:name "exit_status"
                                                                        :type "string" }]}}}})]
    (is (= {:consumes ["application/json"]
            :definitions {"Error" {:additionalProperties false
                                   :properties {:error {:type "string"}
                                                :message {:type "string"}
                                                :metadata {:$ref "#/definitions/ErrorMetadata"}}
                                   :required [:message :error :metadata]
                                   :type "object"}
                          "ErrorMetadata" {:additionalProperties {}, :type "object"}
                          "test.Empty" {:additionalProperties false
                                        :properties {:exit_status {:type "string"}}
                                        :required [:exit_status]
                                        :type "object"}}
            :info {:title "Swagger API: empty", :version "0.0.1"}
            :paths {"/test" {:post {:description ""
                                    :parameters [{:description ""
                                                  :in "body"
                                                  :name "test.Empty"
                                                  :required true
                                                  :schema {:$ref "#/definitions/test.Empty"}}]
                                    :responses {200 {:description ":no-doc:", :schema {}}
                                                410 {:description "Request data didn't conform to the request data schema"
                                                     :schema {:$ref "#/definitions/Error"}}
                                                500 {:description "Internal server error, or response couldn't be serialized according to the response schema"
                                                     :schema {:$ref "#/definitions/Error"}}}
                                    :summary "/test"}}}
            :produces ["application/json"]
            :swagger "2.0"}
           conf))))


(deftest name-mangling
  (let [conf (swag/generate {:name "empty"
                                        ; :mangle-names? true
                             :snake-case-names? true
                             :endpoints {
                                         "/test" { :request {:type "record"
                                                             :name "test.Empty"
                                                             :fields [ {:name "status_field" :type "string" }]}}}})]
    (is (= {:consumes ["application/json"]
            :definitions {"Error" {:additionalProperties false
                                   :properties {:error {:type "string"}
                                                :message {:type "string"}
                                                :metadata {:$ref "#/definitions/ErrorMetadata"}}
                                   :required [:message :error :metadata]
                                   :type "object"}
                          "ErrorMetadata" {:additionalProperties {}, :type "object"}
                          "test.Empty" {:additionalProperties false
                                        :properties {:status-field {:type "string"}}
                                        :required [:status-field]
                                        :type "object"}}
            :info {:title "Swagger API: empty", :version "0.0.1"}
            :paths {"/test" {:post {:description ""
                                    :parameters [{:description ""
                                                  :in "body"
                                                  :name "test.Empty"
                                                  :required true
                                                  :schema {:$ref "#/definitions/test.Empty"}}]
                                    :responses {200 {:description ":no-doc:", :schema {}}
                                                410 {:description "Request data didn't conform to the request data schema"
                                                     :schema {:$ref "#/definitions/Error"}}
                                                500 {:description "Internal server error, or response couldn't be serialized according to the response schema"
                                                     :schema {:$ref "#/definitions/Error"}}}
                                    :summary "/test"}}}
            :produces ["application/json"]
            :swagger "2.0"}
           conf))))
