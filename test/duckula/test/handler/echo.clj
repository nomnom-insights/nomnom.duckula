(ns duckula.test.handler.echo)

(defn handler
  "No validation, echoes whatever"
  [req]
  {:status 200
   :body {:echo (str "echo = " (:body req))}})
