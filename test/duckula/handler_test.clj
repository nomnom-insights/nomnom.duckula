(ns duckula.handler-test
  (:require [clojure.test :refer :all]
            [duckula.protocol]
            [duckula.handler :as handler]))

(deftest metric-keys
  (testing "regular case"
    (is (= {"/bar/baz/ok-how-about-this" ["foo.bar.baz.ok-how-about-this"
                                          "foo.bar.baz.ok-how-about-this.success"
                                          "foo.bar.baz.ok-how-about-this.error"
                                          "foo.bar.baz.ok-how-about-this.failure"]
            "/one/two/three" ["foo.one.two.three"
                              "foo.one.two.three.success"
                              "foo.one.two.three.error"
                              "foo.one.two.three.failure"]}
           (handler/build-metric-keys {:name "foo"
                                       :endpoints {"/one/two/three" {:handler (fn [])}
                                                   "/bar/baz/ok-how-about-this" {:handler (fn [])}}}))))
  (testing "with prefix"
    (is (= {"/api/bar/baz/ok-how-about-this" ["foo.api.bar.baz.ok-how-about-this"
                                              "foo.api.bar.baz.ok-how-about-this.success"
                                              "foo.api.bar.baz.ok-how-about-this.error"
                                              "foo.api.bar.baz.ok-how-about-this.failure"]
            "/api/one/two/three" ["foo.api.one.two.three"
                                  "foo.api.one.two.three.success"
                                  "foo.api.one.two.three.error"
                                  "foo.api.one.two.three.failure"]}
           (handler/build-metric-keys {:name "foo"
                                       :prefix "/api"
                                       :endpoints {"/one/two/three" {:handler (fn [])}
                                                   "/bar/baz/ok-how-about-this" {:handler (fn [])}}})))))

(deftest route-map-builder
  (let [search-handler (fn [])
        echo-handler (fn [])
        route-map (handler/build-route-map
                   {:name "test-server-rpc"
                    :prefix "test-pref"
                    :endpoints {"/search/test" {:request "search/test/Request"
                                                :response "search/test/Response"
                                                :handler search-handler}
                                "/echo" {:handler echo-handler}}})]
    (is (= echo-handler (get-in route-map ["test-pref/echo" :handler])))
    (is (fn? (get-in route-map ["test-pref/search/test" :request])))))

(deftest composite-schema-builder
  (let [search-handler (fn [])
        static-response {:id "foo"
                         :results [{:name "test" :priority 0}
                                   {:name "foo" :priority 10}]}
        route-map (handler/build-route-map
                   {:name "test-server-rpc"
                    :prefix "test-pref"
                    :endpoints {"/search/get" {:response ["search/get/TagItem"
                                                          "search/get/Response"]
                                               :handler (fn [& _args]
                                                          static-response)}}})
        validator (get-in route-map ["test-pref/search/get" :response])]
    (is (fn? validator))
    (is (= static-response
           (validator static-response)))))

(defn create-monitoring [metric-store]
  (reify
    duckula.protocol/Monitoring
    (on-success [this key _response]
      (swap! metric-store (fn [store]
                            (update store key (fn [value]
                                                (inc (or value 0)))))))
    (on-error [this key]
      (swap! metric-store (fn [store] (assoc store key 1))))
    (on-failure [this key]
      (swap! metric-store (fn [store] (assoc store key 1))))
    (record-timing [this key val]
      (swap! metric-store (fn [store]
                            (assoc store (str key ".timing") val))))
    (track-exception [this err]
      (swap! metric-store (fn [store]
                            (update store :exceptions conj err))))
    (track-exception [this err data]
      (swap! metric-store (fn [store]
                            (update store :exceptions conj {:err err :data data}))))
    (on-not-found [this key uri]
      (swap! metric-store (fn [store]
                            (assoc store key uri))))))

(deftest recorded-metrics
  (testing "tracking success"
    (let [handler (handler/build {:name "test"
                                  :endpoints {"/echo" {:handler (fn [_]
                                                                  (Thread/sleep 100)
                                                                  {:status 207 :body ""})}}})
          metric-store (atom {:exceptions []})
          response (handler {:uri "/echo"
                             :component {:monitoring (create-monitoring metric-store)}
                             :body {}})]
      (is (= 207
             (:status response)))
      (is (= 1
             (get @metric-store "test.echo.success")))
      (is (>=
           (get @metric-store "test.echo.timing")
           100))))
  (testing "tracking errors"
    (let [handler (handler/build {:name "test"
                                  :endpoints {"/multiply" {:request "number/multiply/Request"
                                                           :handler (fn [_]
                                                                      (Thread/sleep 100)
                                                                      {:status 207 :body ""})}}})
          metric-store (atom {:exceptions []})
          response (handler {:uri "/multiply"
                             :component {:monitoring (create-monitoring metric-store)}
                             :body {}})]
      (is (= 410
             (:status response)))
      (is (= 1 (count (:exceptions  @metric-store))))
      (is (= 1
             (get @metric-store "test.multiply.failure")))))
  (testing "tracking failures"
    (let [handler (handler/build {:name "test"
                                  :endpoints {"/echo" {:handler (fn [_]
                                                                  (throw (ex-info "noooo" {}))
                                                                  {:status 207 :body ""})}}})
          metric-store (atom {:exceptions []})
          response (handler {:uri "/echo"
                             :component {:monitoring (create-monitoring metric-store)}
                             :body {}})]
      (is (= 500
             (:status response)))
      (is (nil? (get @metric-store "test.echo.success")))
      (is (= "noooo"
             (.getMessage (:err (first (:exceptions @metric-store))))))

      (is (= 1
             (get @metric-store "test.echo.failure"))))))
