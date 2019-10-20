(ns duckula.handler-test
  (:require [clojure.test :refer :all]
            [stature.metrics.protocol]
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
  (let [metric-store (atom {})
        fake-statsd (reify
                      stature.metrics.protocol/Metrics
                      (count [this key]
                        (swap! metric-store (fn [store]
                                              (update store key (fn [value]
                                                                  (inc (or value 0)))))))
                      (gauge [this key val]
                        (swap! metric-store (fn [store]
                                              (println ::gauge)
                                              (assoc store (str key ".gauge") val))))
                      (timing [this key val]
                        (swap! metric-store (fn [store]
                                              (assoc store (str key ".timing") val)))))
        handler (handler/build {:name "test"
                                :endpoints {"/echo" {:handler (fn [_]
                                                                (Thread/sleep 100)
                                                                {:status 207 :body ""})}}})
        response (handler {:uri "/echo"
                           :component {:statsd fake-statsd}
                           :body {}})]
    (is (= 207
           (:status response)))
    (is (= 1
           (get @metric-store "test.echo.success")))
    (is (>=
         (get @metric-store "test.echo.timing")
         100))))
