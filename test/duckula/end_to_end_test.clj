(ns duckula.end-to-end-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [duckula.avro]
   [duckula.test.server :as test.server]
   [clj-http.client :as http.client]))

(use-fixtures :once (fn [t]
                      (test.server/start!)
                      (t)
                      (test.server/stop!)))

(defn body
  [r]
  (try
    (-> r
        :body
        (json/parse-string true))
    (catch Exception _e
      (:body r))))

(deftest it-validates-ins-and-outs
  (testing "invalid input"
    (let [response (http.client/post "http://localhost:3003/search/test" {:content-type :json
                                                                          :accept :json
                                                                          :throw-exceptions false
                                                                          :form-params {:foo :bar}})]
      (is (= 410
             (:status response)))
      (is (= {:message "Request failed"
              :error "Value does not match schema: {:order_by missing-required-key, :query missing-required-key, :foo disallowed-key}"
              :metadata {:schema-name "search.test.Request"
                         :soft-validate? nil
                         :validation-type "duckula.handler/request"}}
             (body response)))))
  ;; as per number.multiply/handler - passing 42 makes it return not a number
  (testing "invalid output"
    (let [response (http.client/post "http://localhost:3003/number/multiply" {:content-type :json
                                                                              :accept :json
                                                                              :throw-exceptions false
                                                                              :body (json/generate-string {:input 42})})]
      (is (= 500
             (:status response)))
      (is (= {:message "Request failed"
              :metadata {:schema-name "number.multiply.Response"
                         :soft-validate? nil
                         :validation-type "duckula.handler/response"}}
             (dissoc (body response) :error)))
      (is (re-find #"Value does not match schema:.+result.+not.+integer.+42"
                   (:error (body response))))))
  (testing "invalid input, but it still returns because of soft validation"
    (let [response (http.client/post "http://localhost:3003/number/multiply-soft" {:content-type :json
                                                                                   :accept :json
                                                                                   :throw-exceptions false
                                                                                   :form-params {:input 42}})]
      (is (= 200
             (:status response)))
      (is (= {:message "funky"
              :result "42"
              :status "success"}
             (body response)))))
  (testing "valid input + valid output"
    (let [response (http.client/post "http://localhost:3003/search/test" {:content-type :json
                                                                          :accept :json
                                                                          :throw-exceptions false
                                                                          :form-params {:query "oh"
                                                                                        :order_by "created_at"}})]
      (is (= 200
             (:status response)))
      (is (= {:status "success"
              :items [{:content "query: oh order created_at size "
                       :id 1}]}
             (body response)))))
  (testing "no validation"
    (let [response (http.client/post "http://localhost:3003/echo" {:content-type :json
                                                                   :accept :json
                                                                   :throw-exceptions false
                                                                   :form-params {:query "oh"
                                                                                 :order_by "created_at"}})]
      (is (= 200
             (:status response)))
      (is (= {:echo "echo = {:query \"oh\", :order_by \"created_at\"}"}
             (body response)))))
  (testing "not found endpoint"
    (let [response (http.client/post "http://localhost:3003/doesnt/exist" {:content-type :json
                                                                           :accept :json
                                                                           :throw-exceptions false
                                                                           :form-params {:query "oh"
                                                                                         :order_by "created_at"}})]
      (is (= 404
             (:status response)))
      (is (= {:message "not found"}
             (body response))))))
