(ns duckula.protocol
  (:refer-clojure :exclude [key]))

(defprotocol Monitoring

  (record-timing
    [this key time-ms]
    "Records the time taken to process the request")

  (on-success
    [this key reponse]
    "Increments given key on success. Also passes the reponse map.")

  (on-error
    [this key]
    "Increments a counter when an error happens e.g. invalid input/output or logic processing error. Validation errors will be counted using this method, but exceptions are not passed here.")

  (on-failure
    [this key]
    "Increments a counter when a failure happens e.g. an unexpected exception during request processing. Should also submit the error to an exception tracker using +track-exception+")

  (on-not-found
    [this key uri]
    "Increments a counter on an endpoint not found")

  (track-exception
    [this exception data]
    [this exception]
    "Tracks that an exception was thrown"))

(defmacro with-timing
  "Tiny macro to record timing of a given form."
  [monitoring ^String key & body]
  `(let [start-time# ^Long (System/currentTimeMillis)
         return# (do
                   ~@body)
         time# ^Long (- (System/currentTimeMillis) start-time#)]
     (record-timing ~monitoring ~key time#)
     return#))
