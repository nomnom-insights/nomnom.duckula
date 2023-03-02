(ns duckula.avro.schema
  "Generates a Prismatic Schema from an Avro schema object -
  ported from https://github.com/cddr/integrity/blob/00326c259e5ff3ab94a37ec032da5a0d08932441/src/integrity/avro.clj
  to support union types and other features"
  (:require
   [clojure.string :as str]
   [schema.core :as s])
  (:import
   (clojure.lang
    Keyword)
   (org.apache.avro
    Schema
    Schema$Field
    Schema$Type)))

(def ByteArray (Class/forName "[B"))

(def any s/Any)

(def null-val (with-meta (s/pred nil?) {:nullable? true}))

(def string s/Str)
(def any-map {Keyword any})

(defn ->map
  [{:keys [avro-schema mangle-names?]}]
  {:pre [avro-schema]}
  (condp = (.getType ^Schema avro-schema)

    ;; Primitive types
    Schema$Type/BOOLEAN s/Bool
    Schema$Type/INT s/Int
    Schema$Type/LONG s/Int
    Schema$Type/FLOAT Float
    Schema$Type/DOUBLE Double
    Schema$Type/BYTES ByteArray
    Schema$Type/STRING s/Str

    ;; Complex Types
    Schema$Type/RECORD
    (apply hash-map (mapcat (fn [key val]
                              (let [{:keys [nullable?]} (meta val)
                                    k (if mangle-names?
                                        (-> key (str/replace \_ \-) keyword)
                                        (keyword key))]
                                (if nullable?
                                  [(s/optional-key k) val]
                                  [k val])))
                            (map #(.name ^Schema$Field %) (.getFields ^Schema avro-schema))
                            (map #(->map {:avro-schema (.schema ^Schema$Field %)
                                          :mangle-names? mangle-names?})
                                 (.getFields ^Schema avro-schema))))

    ;; enums, but accept both :keywords and strings - like Avro layer
    Schema$Type/ENUM
    (apply s/enum (mapcat (fn [enum]
                            (let [enum-val-str (if mangle-names?
                                                 (str/replace enum \_ \-)
                                                 enum)]
                              [enum-val-str (keyword enum-val-str)]))
                          (.getEnumSymbols ^Schema avro-schema)))

    ;; arrays
    Schema$Type/ARRAY
    [(->map {:avro-schema (.getElementType ^Schema avro-schema) :mangle-names? mangle-names?})]

    ;; maps, but accept both strings and keywords as keys
    Schema$Type/MAP
    {(s/cond-pre s/Str Keyword) (->map {:avro-schema (.getValueType ^Schema avro-schema) :mangle-names? mangle-names?})}

    ;; fixed size longs
    Schema$Type/FIXED
    (s/pred (fn [str-val]
              (<= (.getFixedSize ^Schema avro-schema) (count str-val)))
            'exceeds-fixed-size)

    ;; null maps to any
    Schema$Type/NULL
    null-val

    ;; union types resolve to EITHER, but we also need to check if any of the types is null
    ;; so we can mark the keys as optional
    Schema$Type/UNION
    (with-meta
      (apply s/cond-pre (map #(->map {:avro-schema %
                                      :mangle-names? mangle-names?}) (.getTypes ^Schema avro-schema)))

      {:nullable? (some #(= Schema$Type/NULL (.getType ^Schema %)) (.getTypes ^Schema avro-schema))})
    ;; else we have a problem
    {:unknown avro-schema}))
