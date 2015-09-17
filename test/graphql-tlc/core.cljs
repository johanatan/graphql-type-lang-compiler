;; Copyright (c) 2015 Jonathan L. Leonard

(ns graphql-tlc.test.core
  (:require [cljs.test :refer-macros [deftest is async]]
            [cljs.nodejs :as node]
            [graphql-tlc.consumer :as consumer]
            [graphql-tlc.schema :as schema]))

(def gql (node/require "graphql"))

(defn- call-graphql
  ([cb schema query] (call-graphql cb schema query nil))
  ([cb schema query params]
    (.then ((.-graphql gql) schema query params) (fn [res]
      (cb (.stringify js/JSON res nil 2))))))

(defn bail [& _] (throw (js/Error. "Unexpected call.")))

(defn- TestDataResolver [& {:keys [query create modify delete]
                            :or   {query bail create bail modify bail delete bail}}]
  (reify consumer/DataResolver
    (query [_ typename predicate] (query typename predicate))
    (create [_ typename inputs] (create typename inputs))
    (modify [_ typename inputs] (modify typename inputs))
    (delete [_ typename id] (delete typename id))))

(defn file-schema [resolver]
  (first (second (schema/load-schema "./resources/schema.gql" (consumer/GraphQLConsumer resolver)))))

(deftest loads-schema-from-file (is (file-schema (TestDataResolver))))

(deftest simple-select
  (async done
    (let [expected {"data" {"Colors" nil}}
          comparator (fn [s] (is (= expected (js->clj (.parse js/JSON s)))) (done))
          resolver (TestDataResolver :query (fn [typename predicate]
                                              (is (and (= typename "Color") (= predicate "all()"))) nil))]
      (call-graphql comparator (file-schema resolver) "{ Colors { id name } }"))))
