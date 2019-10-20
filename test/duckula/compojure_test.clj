(ns duckula.compojure-test
  "Verifies mounting a duckula handler under a Compojure namespace/prefix"
  (:require [duckula.test.server :as test.server]
            [duckula.handler]
            [compojure.core :as compojure]
            [clj-http.client :as http.client]
            [cheshire.core :as json]
            [clojure.test :refer :all]))

(compojure/defroutes app
  (compojure/GET "/some/endpoint" [] "foo")
  (compojure/context "/rpc-api" []
    (duckula.handler/build (assoc test.server/config
                                  :prefix "/rpc-api"))))

(use-fixtures :once (fn [t]
                      (test.server/start-with-handler! app)
                      (t)
                      (test.server/stop!)))

(deftest it-exposes-validated-api-as-part-of-compojre-router
  (testing "regular route"
    (let [response (http.client/get "http://localhost:3003/some/endpoint")]
      (is (= 200
             (:status response)))
      (is (= "foo"
             (:body response)))))
  (testing "rpc api is mounted under /rpc-api prefix"
    (let [response (http.client/post "http://localhost:3003/rpc-api/number/multiply" {:content-type :json
                                                                                      :accept :json
                                                                                      :throw-exceptions false
                                                                                      :form-params {:input 10}})]
      (is (= 200
             (:status response)))
      (is (= {:status "success"
              :result 20
              :message "funky"}
             (json/parse-string (:body response) true))))))
