(ns duckula.swagger-test
  (:require
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
              :paths {"/foo" {:post {:description "/foo"
                                     :parameters [{:description ""
                                                   :in "body"
                                                   :name ".foo"
                                                   :required true
                                                   :schema {}}]
                                     :responses {200 {:description "", :schema {}}
                                                 410 {:description "Message with invalid schema provided"
                                                      :schema {:$ref "#/definitions/Error"}}
                                                 500 {:description "Internal server error, or response couldn't be serialized according to the response schema"
                                                      :schema {:$ref "#/definitions/Error"}}}
                                     :summary "/foo"}}}
              :produces ["application/json"]
              :swagger "2.0"}
             conf)))))

(deftest working-server-example-config
  (let [conf (swag/generate test.server/config)]
    (is (= :y
           conf))))
