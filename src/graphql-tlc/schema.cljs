;; Copyright (c) 2015 Jonathan L. Leonard

(ns graphql-tlc.schema
  (:require [cljs.nodejs :as node]
            [cljs.core.match :refer-macros [match]]
            [graphql-tlc.common :as common]
            [instaparse.core :as insta]))

(def fs (node/require "fs"))
(def gql (node/require "graphql"))
(def q (node/require "q"))

(def ^:private type-language-parser (insta/parser
  "<S> = TYPE+
  TYPE = <WS> (OBJECT | UNION | ENUM) <WS>
  <OBJECT> = TYPE_KEYWORD <RWS> IDENTIFIER <WS> <'{'> FIELD+ <WS> <'}'>
  TYPE_KEYWORD = 'type'
  RWS = #'\\s+'
  WS = #'\\s*'
  IDENTIFIER = #'[a-zA-Z0-9_]+'
  FIELD = <WS> IDENTIFIER <WS> <':'> <WS> (LIST | DATATYPE) [NOTNULL]
  LIST = <'['> DATATYPE <']'>
  NOTNULL = <'!'>
  DATATYPE = 'ID' | 'Boolean' | 'String' | 'Float' | 'Int' | IDENTIFIER
  <UNION> = UNION_KEYWORD <RWS> IDENTIFIER <WS> <'='> <WS> IDENTIFIER <WS> OR_CLAUSE+
  UNION_KEYWORD = 'union'
  <OR_CLAUSE> = <'|'> <WS> IDENTIFIER <WS>
  <ENUM> = ENUM_KEYWORD <RWS> IDENTIFIER <WS> <'{'> <WS> ENUM_VAL COMMA_ENUM_VAL+ <WS> <'}'>
  ENUM_KEYWORD = 'enum'
  ENUM_VAL = IDENTIFIER
  <COMMA_ENUM_VAL> = <WS> <','> <WS> ENUM_VAL" :output-format :enlive))

(defn- extract-content [m] (get m :content))
(defn- extract-single-content [m] (common/single (extract-content m)))

(defn- extract-field-descriptors [parsed]
  (assert (= :FIELD (get parsed :tag)))
  (let [content (extract-content parsed)
        [field-comp type-comp & not-null-comp] content
        fieldname (extract-single-content field-comp)
        is-list? (= :LIST (get type-comp :tag))
        dt-content (extract-single-content ((if is-list? extract-single-content identity) type-comp))
        datatype (extract-single-content dt-content)
        is-not-null? (= 1 (count not-null-comp))]
    [fieldname datatype is-list? is-not-null?]))

(defn- get-object-descriptors [parsed]
  (assert (= {:tag :TYPE_KEYWORD :content (list "type")} (first parsed)))
  (let [[_ typename-comp & field-comps] parsed
        typename (extract-single-content typename-comp)
        field-descriptors (map extract-field-descriptors field-comps)] [typename field-descriptors]))

(defn- get-union-descriptors [parsed]
  (assert (= {:tag :UNION_KEYWORD :content (list "union")} (first parsed)))
  (let [typename (extract-single-content (second parsed))
        constituents (map #(extract-single-content %) (drop 2 parsed))]
    [typename constituents]))

(defn- get-enum-descriptors [parsed]
  (assert (= {:tag :ENUM_KEYWORD, :content (list "enum")}) (first parsed))
  (let [typename (extract-single-content (second parsed))
        values (map-indexed (fn [i p] {(extract-single-content (extract-single-content p)) {:value (+ 1 i)}}) (drop 2 parsed))]
    [typename values]))

(defprotocol TypeConsumer
  (consume-object [this typename field-descriptors])
  (consume-union [this typename constituents])
  (consume-enum [this typename constituents])
  (finished [this]))

(defmulti load-schema #(.existsSync fs %))

(defmethod load-schema true [filename & consumers]
  (apply load-schema (.toString (.readFileSync fs filename)) consumers))

(defmethod load-schema false [schema-str & consumers]
  (let [parsed (type-language-parser schema-str)]
    (common/dbg-banner-print "Parsed: %s" parsed)
    (doseq [p parsed]
      (assert (= :TYPE (get p :tag)) (common/format "Expected :TYPE. Actual: %s" (get p :tag)))
      (let [content (extract-content p)
            [impl descriptors] (match [(get (first content) :tag)]
              [:UNION_KEYWORD] [consume-union (get-union-descriptors content)]
              [:TYPE_KEYWORD]  [consume-object (get-object-descriptors content)]
              [:ENUM_KEYWORD]  [consume-enum (get-enum-descriptors content)])]
        (doseq [consumer consumers]
          (apply (partial impl consumer) descriptors))))
    [schema-str (doall (map finished consumers))]))
