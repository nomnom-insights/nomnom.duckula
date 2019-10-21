(ns duckula.handler-test
  (:require [clojure.test :refer :all]
            [duckula.protocol]
            [duckula.handler :as handler]))

(deftest metric-keys
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

(deftest recorded-metrics
  (let [metric-store (atom {:exceptions []})
        fake-monitoring (reify
                          duckula.protocol/Monitoring
                          (on-success [this key _response]
                            (swap! metric-store (fn [store]
                                                  (update store key (fn [value]
                                                                      (inc (or value 0)))))))
                          (on-error [this key]
                            (swap! metric-store (fn [store]
                                                  (assoc store (str key ".error") 1))))
                          (on-failure [this key]
                            (swap! metric-store (fn [store]
                                                  (assoc store (str key ".faliure") 1))))
                          (record-timing [this key val]
                            (swap! metric-store (fn [store]
                                                  (assoc store (str key ".timing") val))))
                          (track-exception [this err]
                            (swap! metric-store (fn [store]
                                                  (update store :exceptions conj err))))
                          (track-exception [this err data]
                            (swap! metric-store (fn [store]
                                                  (update store :exceptions conj {:err err :data data})))))
        handler (handler/build {:name "test"
                                :endpoints {"/echo" {:handler (fn [_]
                                                                (Thread/sleep 100)
                                                                {:status 207 :body ""})}}})
        response (handler {:uri "/echo"
                           :component {:monitoring fake-monitoring}
                           :body {}})]
    (is (= 207
           (:status response)))
    (is (= 1
           (get @metric-store "test.echo.success")))
    (is (>=
         (get @metric-store "test.echo.timing")
         100))))
