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
              :paths {"/SOAP/foo" {:post {:description "/SOAP/foo"
                                     :parameters [{:description ""
                                                   :in "body"
                                                   :name ".SOAP.foo"
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


(def test-server-swagger
  (edn/read-string (slurp (io/resource "duckula/test_swagger.edn"))))


(deftest working-server-example-config
  (let [conf (swag/generate test.server/config)]
    (is (= test-server-swagger
           conf))))
