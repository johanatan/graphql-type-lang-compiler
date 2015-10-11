;; Copyright (c) 2015 Jonathan L. Leonard

(ns graphql-tlc.test.core
  (:require [cljs.test :refer-macros [deftest is async]]
            [cljs.nodejs :as node]
            [graphql-tlc.consumer :as consumer]))

(def gql (node/require "graphql"))

(defn- call-graphql
  ([cb schema query] (call-graphql cb schema query nil))
  ([cb schema query params]
    (.then ((.-graphql gql) schema query params) (fn [res]
      (cb (.stringify js/JSON res nil 2))))))

(defn file-schema [resolver] (consumer/get-schema resolver "./resources/schema.gql"))

(deftest loads-schema-from-file (is (file-schema {})))

(deftest simple-select
  (async done
    (let [expected {"data" {"Colors" nil}}
          comparator (fn [s] (is (= expected (js->clj (.parse js/JSON s)))) (done))
          resolver {:query (fn [typename predicate]
                              (is (and (= typename "Color") (= predicate "all()"))) nil)}]
      (call-graphql comparator (file-schema resolver) "{ Colors { id name } }"))))
