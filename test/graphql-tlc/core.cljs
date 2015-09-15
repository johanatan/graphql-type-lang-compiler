(ns graphql-tlc.test.core
  (:require [cljs.test :refer-macros [deftest is]]))

(deftest div-by-zero (is (= js/Infinity (/ 1 0) (/ (int 1) (int 0)))))

(deftest fail (is false true))
