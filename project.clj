(defproject nomnom/duckula "0.7.3"
  :description "RPC server (and soon, client), built on top of JSON+Avro+HTTP"
  :url "https://github.com/nomnom-insights/nomnom.duckula"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.stuartsierra/component "1.1.0"]
                 [nomnom/abracad "0.5.2"]

                 [ring/ring-defaults "0.3.4"]
                 [ring/ring-json "0.5.1"]

                 ;; Dependencies for swagger schema generators
                 ;; and visualizing things
                 [prismatic/schema "1.4.1"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "4.15.5"]]

  :deploy-repositories [["releases" {:sign-releases false :url "https://clojars.org"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org"}]]

  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2018
            :key "mit"}

  :global-vars {*warn-on-reflection* true}

  :profiles {:dev
             {:resource-paths ["dev-resources"]
              :dependencies [[org.clojure/tools.logging "1.2.4"]
                             [ch.qos.logback/logback-classic "1.4.4"
                              :exclusions [org.slf4j/slf4j-api]]
                             ;; test & dev deps
                             [clj-http "3.12.3"]
                             [info.sunng/ring-jetty9-adapter "0.18.1"]

                             [compojure "1.7.0"]
                             [ring/ring-mock "0.4.0"
                              :exclusions [ring/ring-codec]]]}})
