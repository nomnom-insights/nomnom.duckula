(ns duckula.test.handler.search)


(defn handler
  [{:keys [body] :as req}]
  (let [{:keys [query order_by size]} body] ;; TestRequest!
    {:status 200
     :body {:status :success
            ;; we can skip message field, :message "foo"
            :items [{:id 1 :content (str "query: " query " order " order_by " size " size)}]}}))
