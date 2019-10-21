(ns duckula.component.basic-monitoring
  (:refer-clojure :exclude [key])
  (:require [duckula.protocol]
            [clojure.tools.logging :as log]))

(def BasicMonitoring
  (reify duckula.protocol/Monitoring
    (record-timing [this key time-ms]
      (log/infof "request=%s time=%s" key time-ms))
    (on-success [this key response]
      (log/infof "request=%s status=success:%s" key (:status response)))
    (on-error [this key]
      (log/warnf "request=%s status=error" key))
    (on-failure [this key]
      (log/errorf "request=%s status=failure" key))
    (on-not-found [this key uri]
      (log/warnf "request=%s status=not-found uri=%s" key uri))
    (track-exception [this exception] (log/error exception))
    (track-exception [this exception data]
      (log/errorf exception "data=%s" data))))
