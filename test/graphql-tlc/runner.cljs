;; Copyright (c) 2015 Jonathan L. Leonard

(ns graphql-tlc.runner
  (:require [cljs.test :as test]
            [doo.runner :refer-macros [doo-all-tests doo-tests]]
            [graphql-tlc.test.core]))

(doo-tests 'graphql-tlc.test.core)

