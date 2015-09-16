(ns graphql-tlc.test.core
  (:require [cljs.test :refer-macros [deftest is]]
            [graphql-tlc.consumer :as consumer]
            [graphql-tlc.schema :as schema]))

(deftest loads-schema-from-file
  (is (schema/load-schema "./resources/schema.gql" (consumer/GraphQLConsumer))))
