(defproject nomnom/duckula "0.5.0"
  :description "RPC server (and soon, client), built on top of JSON+Avro+HTTP"
  :url "https://github.com/nomnom-insights/nomnom.duckula"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.stuartsierra/component "0.4.0"]
                 [nomnom/abracad "0.5.0"]]
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2018
            :key "mit"}
  :plugins [[lein-cloverage "1.0.13" :exclusions [org.clojure/clojure]]]

  :profiles {:dev
             {:resource-paths ["dev-resources"]
              :dependencies [[org.clojure/tools.logging "0.5.0"]
                             ;; test & dev deps
                             [clj-http "3.10.0"]
                             [ring-jetty-component "0.3.1"
                              :exclude [org.eclipse.jetty/jetty-server]]
                             [org.eclipse.jetty/jetty-server "9.4.21.v20190926"]
                             [ring/ring-defaults "0.3.2"]
                             [ring/ring-json "0.5.0"]
                             [compojure "1.6.1"]
                             [ring/ring-mock "0.4.0"
                              :exclusions [ring/ring-codec]]]}})
