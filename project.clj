(defproject nomnom/duckula "0.7.3"
  :description "RPC server (and soon, client), built on top of JSON+Avro+HTTP"
  :url "https://github.com/nomnom-insights/nomnom.duckula"
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.stuartsierra/component "1.0.0"]
                 [nomnom/abracad "0.5.2"]

                 [ring/ring-defaults "0.3.3"]
                 [ring/ring-json "0.5.1"]

                 ;; Dependencies for swagger schema generators
                 ;; and visualizing things
                 [prismatic/schema "1.2.0"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "4.0.0"]]

  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org"}]]

  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2018
            :key "mit"}

  :global-vars {*warn-on-reflection* true}

  :profiles {:dev
             {:resource-paths ["dev-resources"]
              :dependencies [[org.clojure/tools.logging "1.1.0"]
                             [ch.qos.logback/logback-classic "1.2.7"
                              :exclusions [org.slf4j/slf4j-api]]
                             ;; test & dev deps
                             [clj-http "3.12.3"]
                             [ring-jetty-component "0.3.1"
                              :exclude [ring/ring-codec
                                        org.eclipse.jetty/jetty-server]]
                             ;; do not upgrade to 10.x or 11.x versions
                             [org.eclipse.jetty/jetty-server "9.4.44.v20210927"]
                             [compojure "1.6.2"]
                             [ring/ring-mock "0.4.0"
                              :exclusions [ring/ring-codec]]]}})
