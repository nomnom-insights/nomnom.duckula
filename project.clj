(defproject nomnom/duckula "0.7.2"
  :description "RPC server (and soon, client), built on top of JSON+Avro+HTTP"
  :url "https://github.com/nomnom-insights/nomnom.duckula"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.stuartsierra/component "1.0.0"]
                 [nomnom/abracad "0.5.1"]

                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.0"]


                 ;; Dependencies for swagger schema generators
                 ;; and visualizing things
                 [prismatic/schema "1.1.12"]
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "3.25.3"]]
  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org"}]]

  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2018
            :key "mit"}
  :plugins [[lein-cloverage "1.1.2" :exclusions [org.clojure/clojure]]]

  :profiles {:dev
             {:resource-paths ["dev-resources"]
              :global-vars {*warn-on-reflection* true}
              :dependencies [[org.clojure/tools.logging "1.1.0"]
                             [ch.qos.logback/logback-classic "1.2.3"
                              :exclusions [org.slf4j/slf4j-api]]
                             ;; test & dev deps
                             [clj-http "3.10.1"]
                             [ring-jetty-component "0.3.1"
                              :exclude [ring/ring-codec
                                        org.eclipse.jetty/jetty-server]]
                             [org.eclipse.jetty/jetty-server "9.4.21.v20190926"]
                             [compojure "1.6.1"]
                             [ring/ring-mock "0.4.0"
                              :exclusions [ring/ring-codec]]]}})
