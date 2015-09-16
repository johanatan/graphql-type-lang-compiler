;; Copyright (c) 2015 Jonathan L. Leonard

(ns graphql-tlc.test.core
  (:require [cljs.test :refer-macros [deftest is]]
            [graphql-tlc.consumer :as consumer]
            [graphql-tlc.schema :as schema]))

(def ^:private TestDataResolver
  (reify consumer/DataResolver
    (query [_ typename predicate] nil)
    (create [_ typename inputs] nil)
    (modify [_ typename inputs] nil)
    (delete [_ typename id] nil)))

(deftest loads-schema-from-file
  (is (schema/load-schema "./resources/schema.gql" (consumer/GraphQLConsumer TestDataResolver))))
