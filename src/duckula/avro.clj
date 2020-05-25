(ns duckula.avro
  (:require
    [abracad.avro.codec :as codec]
    [abracad.io :as io]))


(def ^:dynamic *default-path* "schema/endpoint/")
(def ^:dynamic *default-extension* ".avsc")


(defn name->path
  "If `n` is not a map - turn it into the default path to a schema file
  as defined by the prefix `*default-path*` and extension `*default-extension*`"
  [n]
  (cond
    (map? n) n
    (string? n) (str *default-path* n *default-extension*)
    (sequential? n) (mapv name->path n)))


(defn load-schemas
  [& schemas]
  (when (seq schemas)
    (apply codec/parse-schema* (map #(cond
                                       (map? %) %
                                       (string? %) (io/read-json-resource %)
                                       (sequential? %) (map io/read-json-resource %))
                                    (flatten schemas)))))


(defn validate-with-schema
  [schema input]
  (if schema
    (do
      (codec/->avro schema input)
      input)
    input))


(defn make-validator
  "Returns a validator function for given Avro schema map, single schema path, or a list of
  schema paths (will construct a composite schema).
  Attaches schema path(s) to the function as metadata
  Options:
  - mangle-names? - if set to true, it will change all _ to - in key and enum value names
  - soft-validate? - if set to true, it will attach a boolean flag to the function to indicate that
                    the function user can ignore validation error and return the input data (even if invalid)
                    This is useful, if you want to roll out validation, but not break on invalid payloads"
  [schema {:keys [mangle-names? soft-validate?]}]
  {:pre [(or (string? schema)
             (map? schema)
             (sequential? schema))]}
  (let [schema (load-schemas schema)
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
      {:soft-validate? soft-validate? :schema-name schema})))


(defn validator
  "Creates a validator function for given schema paths.
  Will resolve these names to resource paths.
  `schema-name-or-names` can be a string or list of strings.
  See `make-validator` for options explanation"
  ([schema]
   (validator schema {}))
  ([schema opts]
   (if schema
     (make-validator (name->path schema) opts)
     identity)))
