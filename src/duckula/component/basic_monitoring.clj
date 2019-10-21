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
    (on-error [this key exception]
      (log/errorf exception "request=%s status=error" key))
    (on-failure [this key exception]
      (log/errorf exception "request=%s status=failure" key))))
