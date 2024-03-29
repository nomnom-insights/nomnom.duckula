(ns duckula.avro-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [duckula.avro]))

(deftest validation
  (testing "it skips validation if no schema"
    (is (= :foo
           (duckula.avro/validate-with-schema nil :foo))))
  (testing "loads schema and creates a validator"
    (let [validator (duckula.avro/make-validator
                     "schema/endpoint/search/test/Response.avsc"
                     {:mangle-names? false
                      :soft-validate? false})]
      (is (fn? validator))
      (is (= {:schema-name "search.test.Response"
              :soft-validate? false}
             (meta validator)))))
  (testing "validate-with-underscore"
    (let [validator (duckula.avro/make-validator
                     "schema/endpoint/search/test/Request.avsc"
                     {:mangle-names? false})]
      (is (= {:order_by "created_at"
              :query "test"}
             (validator {:query "test" :order_by "created_at"})))
      (is (thrown? Exception
                   (validator {:query "test" :order-by "created_at"})))))
  (testing "validate-with-dashes"
    ;; test that outside the make-validator fn
    ;; clojure structure will be converted to use dashes for schema validation
    (let [validator (duckula.avro/make-validator
                     "schema/endpoint/search/test/Request.avsc"
                     {:mangle-names? true})]
      (is (thrown? Exception
                   (validator {:query "test" :order_by "created_at"})))
      (is (= {:order-by "created-at"
              :query "test"}
             (validator {:query "test" :order-by "created-at"}))))))

(deftest composable-schemas
  (testing "from resources"
    (let [validator (duckula.avro/make-validator
                     ["schema/endpoint/search/get/TagItem.avsc"
                      "schema/endpoint/search/get/Response.avsc"] {:mangle-names? false})]
      (is (fn? validator))
      (let [payload {:id "foo"
                     :results [{:name "test" :priority 0}
                               {:name "foo" :priority 10}]}]
        (is (= payload
               (validator payload))))))
  (testing "inline schemas"
    (let [validator (duckula.avro/make-validator
                     {:name "TagItem"
                      :type "record"
                      :fields [{:name "name" :type "string"}
                               {:name "priority" :type "long"}]}
                     {:mangle-names? false})]
      (is (fn? validator))
      (let [payload {:name "banans" :priority 100}]
        (is (= payload
               (validator payload)))))))
