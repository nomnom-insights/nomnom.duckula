(ns duckula.test.handler.number)

(defn handler
  [{:keys [body] :as _req}]
  (let [{:keys [input]} body]
    ;; echo back input
    {:status 200
     :body {:status :success
            :message "funky"
            :result (if (not= 42 input) ; invalid schema failure injection
                      (* 2 input)
                      (str input))}}))
