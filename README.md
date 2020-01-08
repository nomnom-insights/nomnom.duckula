# duckula


Status
[![CircleCI](https://circleci.com/gh/nomnom-insights/nomnom.duckula.svg?style=svg)](https://circleci.com/gh/nomnom-insights/nomnom.duckula)

Installation:

[![Clojars Project](https://img.shields.io/clojars/v/nomnom/duckula.svg)](https://clojars.org/nomnom/duckula)

<img src="https://vignette.wikia.nocookie.net/danmacgregor/images/4/4e/Count_Duckula.jpg/revision/latest?cb=20100807200739" align="right" height="160px" />

> :warning: While Duckula is used in production by [EnjoyHQ](https://getenjoyhq.com) there are still things we're working out. You have been warned! :warning:

> :warning: If you value **stable** software - wait for the v1, otherwise - here be ducks

Duckula is a synchronous equivalent of [Bunnicula](https://github.com/nomnom-insights/nomnom.bunnicula) bult on top of ring, HTTP, JSON and Avro:

- uses Stuart Sierra's [Component](https://github.com/stuartsierra/component) for dependency injection
- establishes conventions for synchronous HTTP APIs:
  - HTTP POST only
  - JSON for input/output (for now)
  - routes/URIs map to operations (e.g. `POST documents/get-by-id` instead of `GET /documents`), meaning there's no route params
- handlers are functions receiving the request map, along with dependent components
- validates inputs and outputs via Avro schemas
  - supports merging multiple Avro schemas to make it easy to share schemas
- uses protocols to inject a monitoring middleware. We provide our own, which reports metrics to Statsd and errors to Rollbar - see [duckula.monitoring](https://github.com/nomnom-insights/nomnom.duckula.monitoring)
- convention over configuration, where it makes sense


## Roadmap

- [ ] can talk Avro (input and output) via content type negotiation
- [ ] exposes (optionally) endpoint with API documentation based on Avro's doc properites (schemas and schema fields can be documented) in the OpenAPI format
- [ ] equivalent clj-http middleware for building type-safe clients

## Rationale

Based on [our experience of building a Clojure framework for RabbitMQ](https://blog.getenjoyhq.com/bunnicula-asynchronous-messaging-with-rabbitmq-for-clojure/) we learned a good deal about building a *mostly-Clojure* backend which works as a part of a system built using other languages. If your stack is 100% Clojure, Duckula might not be for you. The reason for using Avro and strongly typed validation on *the edges* of the system, rather than Spec or Schema allows us to share schemas with Javascript and Ruby clients and guarantee correctness of inputs/outputs across service boundaries.

While we looked at solutions such as gRPC or GraphQL, neither of them had a good support for our existing tooling, required adopting a completely different approach/tooling/etc or would need a significant effort to migrate. Duckula offers a compromise between using known (to us!) stack, simplicity and is based on our previous attempts at building *frameworks* in Clojure.

By using JSON and HTTP, we can leverage standard tooling such as nginx, curl and `jq`. By using Avro, we get a simple solution for defining schemas *at runtime* and support for multiple languages, not only Clojure. Lack of a compilation step is a huge benefit to the developer productivity.

## Usage

Duckula is mostly config driven. An example config for a "test-rpc-service" would be:

```clojure

(def config
  {:name "some-rpc-service"
   :mangle-names? false ;; default false, see below
   :endpoints { "/search/test" {:request ["shared/Tag" "search/test/Request"] ; re-use schemas
                                :response ["shared/Tag" "search/test/Response"]
                                :handler handler.search/handler} ; request handler
               "/number/multiply" {:request "number/multiply/Request"
                                   :response "number/multiply/Response"
                                   :soft-validate? true ; default false, see below
                                   :handler handler.number/handler}
               ;; no validation
               "/echo" {:handler handler.echo/handler}}})

```

Then in your Component system:

```clojure

(def system-map
  (merge
   {:db (some.db/connection)
    ;; required for metrics and error reporting
    :monitoring duckula.component.basic-monitoring/BasicMonitoring}
   ;; see dev-resources dir for a working example
   ;; at the very least, your ring middleware stack needs to handle
   ;; JSON parsing from the POST body
   (duckula.test.component.http-server/create
    (duckula.handler/build config)
    [:db :monitoring]
    {:port 3000 :name "api"})))

```

(You can see an example web server component example in `dev-resources/duckula/test/component/http-server.clj`)

Duckula will:


- only match endpoints listed under `:endpoints` key
- lookup schemas for each endpoint and use them to validate incoming POST body and response body, note that schemas are optional - you can use Duckula as a simplistic route  with metrics and error reporting built-in
- request handler function will receive the full request map, along with component dependencies
- when serving requests it will track:
  - request time (for `/search/test` it would record latency under `some-rpc-service.search.test`)
  - number of successfully handled requests under `some-rpc-service.search.test.success`
  - number of errored (invalid input etc) handled requests under `some-rpc-service.search.test.error`
  - number of failed (exceptions) handled requests under `some-rpc-service.search.test.failure`
- log/report exceptions (if any)
- when schemas fail to validate it responds with standard error response and info about which schema and when it failed
- if a route doesn't exist it will respond with standard 404 and record metrics

### `mangle-names?`

By default all map keys and enum values have to use `_` (underscore) as word separators. That's true for inputs (POST data) and outputs (JSON responses). That also means, that all keys with `-` dashes in key names, will be replaced with `_` underscores. See more info about schema mangling here: https://github.com/nomnom-insights/abracad#basic-deserialization

If you want to enable automatic conversion of underscores to dashes (and make underscored names invalid) set `mangle-names?` to true.

#### Example

```json
 {
  "name" : "Request",
  "fields" : [
  {
      "name" : "order_by",
      "type" : {
        "name" : "OrderBy",
        "type" : "enum",
        "symbols" : [
          "created_at",
          "updated_at"
        ]
      }
    }
  ]
}
```

When `mangle-names?` is set to false (**default**) the following payload is *valid*: `{order_by:  "updated_at"}` .

When `mangle-names?` is set to true the example payload would be **invalid** and this would be required: `{order-by: "updated-at"}`

### `soft-validate?`

When set to true Duckula will perform input and output validation, but **will still pass request and response data** to/from the request handler function even if it's not conforming to the given schema. Use case for that is adding a schema to an existing endpoint or rolling out changes to the existing schema, but only to see if there's any invalid data being sent in/out, without affecting actual request processing.
**Note** - this means that your handler functions still have to deal with potentially invalid input, as you might receive request body which is not correct!


### Schema loading and merging

You can pass a resource path to a single schema, and it will be looked up in resource paths, with the `schema/endpoint` prefix.

Example: `search/get/Request` will be resolved to `schema/endpoint/search/get/Request.avsc`.
You can configure the endpoints to merge schemas, for re-use of parts by passing a vector of schemas:

```clojure

{ :endpoints { "/test"  { :request ["shared/Tag" "shared/User" "test/Request" ]
                          :response ["shared/Tag" "shared/User" "test/Response" ]
                          :handler test-fn } } }
```

# Monitoring

Theonl only hard dependency is the monitoring component, which implements `duckula.protcol/Monitoring` protocol. A sample implementation can be found in `duckula.component.monitoring` namespace.

We have a complete, production grade implementation based on [Caliban](https://github.com/nomnom-insights/nomnom.caliban) for reporting exceptions to Rollbar, and [Stature](https://github.com/nomnom-insights/nomnom.stature) for recording metrics to a Statsd server.

See it here: https://github.com/nomnom-insights/nomnom.duckula.monitoring

# Usage

### As a standalone handler in a web server

```clojure
(ns duckula.server
  "Test HTTP server"
  (:require [duckula.test.component.http-server :as http-server]
            duckula.handler
            [duckula.component.basic-monitoring :as monit]
            [duckula.handler.echo :as handler.echo]
            [duckula.handler.number :as handler.number]
            [duckula.handler.search :as handler.search]
            [com.stuartsierra.component :as component]))

(def server (atom nil))

;; Assumptions:
;; Avro schemas exist somewhere in  CLASSPATH, under schema/endpoint/ directory
;; So here 'search/test/Response' is looked up in `schema/endpoint/search/test/Response.avsc`
;; If rquest and/or response keys are nil, then we default  to `identity` as the validation function
;; meaning, there's no validation :-)
(def config
  {:name "some-rpc-service"
   :endpoints {"/search/test" {:request "search/test/Request"
                              :response "search/test/Response"
                              :handler handler.search/handler}
   "/number/multiply" {:request "number/multiply/Request"
                       :response "number/multiply/Response"
                       :handler handler.number/handler}
   ;; no validation
   "/echo" {:handler handler.echo/handler}}})

(defn start! []
  (let [sys (component/map->SystemMap
             (merge
              {:monitoring monit/BasicMonitoring}
              (http-server/create (duckula.handler/build config)
                                  [:monitoring]
                                  {:name "test-rpc-server"
                                   :port 3003})))]
    (reset! server (component/start sys))))

(defn stop! []
  (swap! server component/stop))
```

### As part of an existing ring application

An example of how to add Duckula powered routes to an existing Compojure-based app:

```clojure

(def config
  {:endpoints { "/search" {:request "groups/search/Request"
                         :response "groups/search/Response"
                         :handler service.http.handler.groups/search}
               "/create" {:request "groups/create/Request"
                          :response "groups/create/Response"
                          :handler service.http.handler.groups/create}
               "/ping" {:handler service.http.handler.groups/ping}}
   :name "groups-rpc"
   :prefix "/groups" ; Must match Compojure context below
   })


;; assumes we're using compojure

(defroutes all
  (context "/groups" [] (duckula.handler/build config))
  (context "/dashboards" [] service.http.handlers.dashboards/routes))


```

# Changelog


## [0.5.1] - 2019-12-26

*Unreleased*

Bug fix release - fixes an issue with metrics reporting for namespaced routes. Available as `0.5.1-SNAPSHOT`

## [0.5.0] - 2019-10-23

Initial public release

# Authors

<sup>In alphabetical order</sup>

- [Afonso Tsukamoto](https://github.com/AfonsoTsukamoto)
- [Łukasz Korecki](https://github.com/lukaszkorecki)
- [Marketa Adamova](https://github.com/MarketaAdamova)
