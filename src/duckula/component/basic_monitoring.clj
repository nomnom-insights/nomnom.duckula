(ns duckula.component.basic-monitoring
  (:refer-clojure :exclude [key])
  (:require
    [clojure.tools.logging :as log]
    [duckula.protocol]))


(defrecord BasicMonitoring []
  duckula.protocol/Monitoring
  (record-timing [_this key time-ms]
    (log/infof "request=%s time=%s" key time-ms))
  (on-success [_this key response]
    (log/infof "request=%s status=success:%s" key (:status response)))
  (on-error [_this key]
    (log/warnf "request=%s status=error" key))
  (on-failure [_this key]
    (log/errorf "request=%s status=failure" key))
  (on-not-found [_this key uri]
    (log/warnf "request=%s status=not-found uri=%s" key uri))
  (track-exception
    [_this exception] (log/error exception))
  (track-exception
    [_this exception data]
    (log/errorf exception "data=%s" data)))

(def basic (->BasicMonitoring))
