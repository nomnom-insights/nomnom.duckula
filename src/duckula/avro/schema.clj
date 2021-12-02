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

(def string s/Str)
(def any-map {Keyword any})


(defn ->map
  [{:keys [avro-schema mangle-names?]}]
  (condp = (.getType ^Schema avro-schema)

    ;; Primitive types
    Schema$Type/BOOLEAN s/Bool
    Schema$Type/INT     Integer
    Schema$Type/LONG    Long
    Schema$Type/FLOAT   Float
    Schema$Type/DOUBLE  Double
    Schema$Type/BYTES   ByteArray
    Schema$Type/STRING  s/Str

    ;; Complex Types
    Schema$Type/RECORD
    (apply hash-map (mapcat (fn [key val]
                              (let [k (if mangle-names?
                                        (-> key (str/replace \_ \-) keyword)
                                        (keyword key))]
                                [k val]))
                            (map #(.name ^Schema$Field %) (.getFields ^Schema avro-schema))
                            (map #(->map {:avro-schema (.schema ^Schema$Field %)
                                          :mangle-names? mangle-names?})
                                 (.getFields ^Schema avro-schema))))

    Schema$Type/ENUM
    (apply s/enum (map (fn [enum]
                         (if mangle-names?
                           (str/replace enum \_ \-)
                           enum))
                       (.getEnumSymbols ^Schema avro-schema)))

    Schema$Type/ARRAY
    [(->map {:avro-schema (.getElementType ^Schema avro-schema) :mangle-names? mangle-names?})]

    Schema$Type/MAP
    {s/Str (->map {:avro-schema  (.getValueType ^Schema avro-schema) :mangle-names? mangle-names?})}

    Schema$Type/FIXED
    (s/pred (fn [str-val]
              (<= (.getFixedSize ^Schema avro-schema) (count str-val)))
            'exceeds-fixed-size)

    Schema$Type/NULL
    s/Any
    Schema$Type/UNION
    (apply s/either (map #(->map {:avro-schema %
                                  :mangle-names? mangle-names?}) (.getTypes ^Schema avro-schema)))
    ;; else
    {:unknown avro-schema}))
