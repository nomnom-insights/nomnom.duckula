(ns duckula.avro
  (:require [abracad.avro.codec :as codec]
            [abracad.io :as io]))

(def ^:dynamic *default-path* "schema/endpoint/")
(def ^:dynamic *default-extension* ".avsc")

(defn name->path [n]
  (cond
    (string? n) (str *default-path* n *default-extension*)
    (sequential? n) (mapv name->path n)))

(defn load-schema* [names]
  (cond
    (string? names) (-> names
                        io/read-json-resource
                        codec/parse-schema)
    (sequential? names) (->> names
                             (mapv io/read-json-resource)
                             (apply codec/parse-schema*))))

(def load-schemas (memoize load-schema*))

(defn validate-with-schema [schema input]
  (if schema
    (do
      (codec/->avro schema input)
      input)
    input))

(defn make-validator
  "Returns a validator function for given Avro schema path, or a list of
  schema paths (will construct a composite schema).
  Attaches schema path(s) to the function as metadata
  Options:
  - mangle-names? - if set to true, it will change all _ to - in key and enum value names
  - soft-validate? - if set to true, it will attach a boolean flag to the function to indicate that
                    the function user can ignore validation error and return the input data (even if invalid)
                    This is useful, if you want to roll out validation, but not break on invalid payloads"
  [schema-names {:keys [mangle-names? soft-validate?]}]
  {:pre [(or (string? schema-names)
             (sequential? schema-names))]}
  (let [schema (load-schemas schema-names)
        validator-fn (if mangle-names?
                       (fn [input]
                         (validate-with-schema schema input))
                       ;; in order to support underscore in json payload we need to disable mangle-names
                       ;; this means we can't support dashes in any of the keys
                       ;; in json payload (everything needs to use underscore !!!)
                       ;; in case you want to support dashes in the payload
                       ;; keys & values you can set optional-conf mangle-names? to true
                       ;; this is just local setting and won't effect avro functionality outside of this fn
                       (fn [input]
                         (with-bindings {#'abracad.avro.util/*mangle-names* false}
                           (validate-with-schema schema input))))]
    (with-meta
      validator-fn
      {:soft-validate? soft-validate? :schema-name schema-names})))

(defn validator
  "Creates a validator function for given schema paths.
  Will resolve these names to resource paths.
  `schema-name-or-names` can be a string or list of strings.
  See `make-validator` for options explanation"
  ([schema-name-or-names]
   (validator schema-name-or-names {}))
  ([schema-name-or-names opts]
   (if schema-name-or-names
     (make-validator (name->path schema-name-or-names) opts)
     identity)))
