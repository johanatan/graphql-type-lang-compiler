;; Copyright (c) 2015 Jonathan L. Leonard

(ns graphql-tlc.common
  (:require [goog.string.format]
            [cljs.pprint :as pprint]
            [clojure.string :as string]
            [cljs.nodejs :as node]))

(enable-console-print!)

(def fs (node/require "fs"))

(def DEBUG (atom false))
(defn pprint-str [obj] (pprint/write obj :stream nil))
(defn dbg-print [fmt & args] (if @DEBUG
                               (let [ppargs (map #(pprint-str %) args)]
                                 (console/log (apply (partial format fmt) ppargs)))))
(defn dbg-banner-print [fmt & args]
  (let [banner (string/join (repeat 85 "="))]
    (dbg-print (string/join [banner "\n" fmt "\n" banner]) args)))
(defn dbg-obj-print [obj] (console/log obj) obj)
(defn dbg-obj-print-in [props obj] (console/log (apply (partial aget obj) props)) obj)
(defn dbg-file [msg] ;; GraphQL eats console output occuring in our callbacks.
  (.appendFileSync fs "./debug.log" (format "%s\n" (pprint-str msg))) msg)

(defn jskeys [jsobj]
  (.keys js/Object jsobj))

(defn format
  "Formats a string using goog.string.format."
  [fmt & args]
  (apply goog.string/format fmt args))

(defn single [col] (assert (= 1 (count col))) (first col))

(defn pluralize [noun] (format "%s%s" noun (if (goog.string/endsWith noun "s") "es" "s")))

