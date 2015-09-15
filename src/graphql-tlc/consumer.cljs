;; Copyright (c) 2015 Jonathan L. Leonard

(ns graphql-tlc.consumer
  (:require [graphql-tlc.common :as common]
            [graphql-tlc.schema :as schema]
            [clojure.string :as string]
            [clojure.set :as set]
            [cljs-uuid-utils.core :as uuid]
            [cljs.nodejs :as node]))

(def ^:private gql (node/require "graphql"))
(def ^:private q (node/require "q"))
(def ^:private zmq (node/require "zmq"))

(defprotocol DataResolver
  (query [this typename predicate])
  (create [this typename inputs])
  (modify [this typename inputs])
  (delete [this typename id]))

(def ^:private send-async-message
  (let [deferred-map (atom {})
        requester (let [req (.socket zmq "req")]
            (.connect req "tcp://localhost:5560")
            (.on req "message" (fn [msg]
              (common/dbg-file (common/format "Received response: '%s'" msg))
              (let [[uid res] (string/split msg #"\|")
                     promis (get @deferred-map uid)]
                (.resolve promis (.parse js/JSON res))
                (swap! deferred-map dissoc uid)))))]
    (fn [message]
      (let [uid (.toString (uuid/make-random-uuid))
            msg (common/format "%s:%s" uid message)
            deferred (.defer q)]
        (swap! deferred-map assoc uid deferred)
        (common/dbg-file (common/format "Sending request: '%s'" msg))
        (.send requester msg)
        (.-promise deferred)))))

(def ^:private DataServiceConsumer
  (letfn [(assert-success [promise]
            (.then promise (fn [json]
              (assert (= json (.parse js/JSON "{'result':'success'}")), (common/format "Backend failure: %s" json)))))]
    (let [checked-send-async (comp assert-success send-async-message (partial common/format "$SCHEMA$:%s"))
          extract-enum-value (fn [m] (let [s (seq m) f (first s)] [(key f) (get (val f) :value)]))
          messages (atom ())
          batch-message (fn [obj typename params] (swap! messages conj (common/format "%s|%s|%s" obj typename params)))]
      (reify schema/TypeConsumer
        (consume-object [_ typename fields] (batch-message "obj" typename fields))
        (consume-union [_ typename constituents] (batch-message "union" typename constituents))
        (consume-enum [_ typename constituents] (batch-message "enum" typename (map extract-enum-value constituents)))
        (finished [_] (checked-send-async (string/join "!" @messages)))))))

(def ^:private DataServiceResolver
  (reify DataResolver
    (query [_ typename predicate]
      (letfn [(async-query [typename predicate]
                (send-async-message (common/format "GET|%s|%s" typename predicate)))]
        (apply async-query (if (not= -1 (.indexOf predicate "$"))
          (let [[fieldname rst] (string/split predicate #"=")
                [entity fldval] (string/split rst #"\$")]
            [entity (common/format "%s=%s" fieldname fldval)])
          [typename predicate]))))
    (create [_ typename inputs]
      (send-async-message (common/format "CREATE|%s|%s" typename inputs)))
    (modify [_ typename inputs]
      (send-async-message (common/format "UPDATE|%s|%s" typename inputs)))
    (delete [_ typename id]
      (send-async-message (common/format "DELETE|%s|%s" typename id)))))

(def ^:private primitive-types {
  "ID"      gql.GraphQLID
  "Boolean" gql.GraphQLBoolean
  "String"  gql.GraphQLString
  "Float"   gql.GraphQLFloat
  "Int"     gql.GraphQLInt })

(defn GraphQLConsumer []
  (let [data-resolver DataServiceResolver
        type-map (atom primitive-types)
        fields-map (atom {})
        unions (atom [])
        enums (atom {})]
    (letfn [
      (get-by-id [typename id]
        (query data-resolver typename (common/format "id=%s" (js->clj id))))
      (is-enum? [typ]
        (contains? (set (keys @enums)) typ))
      (is-primitive? [typ]
        (or (is-enum? typ) (contains? (set (keys primitive-types)) typ)))
      (modify-type [typ is-list? is-not-null?]
        (let [list-comb (if is-list? #(new gql.GraphQLList %) identity)
              not-null-comb (if is-not-null? #(new gql.GraphQLNonNull %) identity)
              composed (comp not-null-comb list-comb)
              res (composed typ)] res))
      (get-type [typ is-list? is-not-null?] (modify-type (get @type-map typ) is-list? is-not-null?))
      (get-resolver [datatype is-list? fieldname]
        (if (not (is-primitive? datatype))
          {:resolve (fn [parent] (if is-list? (clj->js (map (fn [id] (get-by-id datatype id)) (aget parent fieldname)))
                      (get-by-id datatype (aget parent fieldname))))}))
      (get-field-spec [[fieldname datatype is-list? is-not-null?]]
        (let [typ (get-type datatype is-list? is-not-null?)
              resolver (get-resolver datatype is-list? fieldname)
              res {fieldname (merge {:type typ} resolver)}] res))]
      (reify schema/TypeConsumer
        (consume-object [_ typename field-descriptors]
          (let [field-specs (map get-field-spec field-descriptors)
                fieldnames (map first field-descriptors)
                merged (delay (apply merge field-specs))
                descriptors {:name typename :fields (fn [] (clj->js @merged))}
                res (gql.GraphQLObjectType. (clj->js descriptors))]
            (assert (not (contains? @type-map typename))
              (common/format "Duplicate type name: %s" typename))
            (assert (not (contains? @fields-map fieldnames))
              (common/format "Duplicate field set: %s" (common/pprint-str fieldnames)))
            (assert (contains? (set fieldnames) "id")
              (common/format "Type must contain an 'id' field. Fields: %s" (common/pprint-str fieldnames)))
            (swap! type-map assoc typename res)
            (swap! fields-map assoc fieldnames [typename (map rest field-descriptors)])
            (console/log (common/format "Created object type: %s" typename))
            (get @type-map typename)))
        (consume-union [_ typename constituents]
          (let [types (map #(get @type-map %) constituents)
                descriptor {:name typename :types types
                            :resolveType (fn [value] (let [fields (keys (js->clj value))]
                                                       (get @type-map (first (get @fields-map fields)))))}
                res (gql.GraphQLUnionType. (clj->js descriptor))]
            (swap! type-map assoc typename res)
            (swap! unions concat [typename])
            (console/log (common/format "Created union type: %s: descriptor: %s" typename descriptor)) [res descriptor]))
        (consume-enum [_ typename constituents]
          (let [descriptor {:name typename :values (apply merge constituents)}
                res (gql.GraphQLEnumType. (clj->js descriptor))]
            (swap! type-map assoc typename res)
            (swap! enums assoc typename constituents)
            (console/log (common/format "Created enum type: %s: descriptor: %s" typename descriptor)) [res descriptor]))
        (finished [_]
          (let [union-input-type-desc { :name (common/format "Union")
                                        :fields { :type { :type (gql.GraphQLNonNull. gql.GraphQLString)}
                                                  :id { :type (gql.GraphQLNonNull. gql.GraphQLID)}}}
                union-input-type (gql.GraphQLInputObjectType. (clj->js union-input-type-desc))]
            (letfn [
              (get-query-descriptors [typ]
                (let [res
                  [{typ
                     {:type (get @type-map typ)
                      :args {:id {:type (gql.GraphQLNonNull. gql.GraphQLID)}}
                      :resolve (fn [root obj] (get-by-id typ (get (js->clj obj) "id")))}}
                  {(common/pluralize typ)
                    {:type (gql.GraphQLList. (get @type-map typ))
                     :resolve (fn [root] (query data-resolver typ "all()"))}}]]
                  (common/dbg-print "Query descriptors for typename: %s: %s" typ res) res))
              (get-args [typ req-mod?]
                (letfn [
                  (get-ref-type [typ] (cond (is-enum? typ) (get @type-map typ)
                                            (contains? (set @unions) typ) union-input-type
                                            :else gql.GraphQLID))
                  (get-mutation-arg-type [[typ is-list? is-non-null?]]
                    (modify-type (or (get primitive-types typ) (get-ref-type typ)) is-list? (if req-mod? is-non-null? false)))]
                  (let [kvs (seq @fields-map)
                        kv (common/single (filter #(= (first (second %)) typ) kvs))
                        pairs (partition 2 (interleave (first kv) (second (second kv))))
                        sans-id (remove #(= (first %) "id") pairs)
                        res (map (fn [pair] {(first pair) {:type (get-mutation-arg-type (second pair))}}) sans-id)] res)))
              (get-mutations [typ]
                (letfn [
                  (get-id [o] (get o "id"))
                  (get-mutation [prefix resolver req-mod? get-args? transform args] {(common/format "%s%s" prefix typ) {
                    :type (get @type-map typ)
                    :args (into args (if get-args? (get-args typ req-mod?) {}))
                    :resolve (fn [root obj] (resolver data-resolver typ (transform (js->clj obj))))}})]
                  (let [res [(get-mutation "create" create true true identity {})
                             (get-mutation "update" modify false true identity {:id {:type (gql.GraphQLNonNull. gql.GraphQLID)}})
                             (get-mutation "delete" delete false false get-id {:id {:type (gql.GraphQLNonNull. gql.GraphQLID)}})]]
                    (common/dbg-print "Mutation descriptors for typename: %s: %s" typ res) res)))
              (create-obj-type [name fields]
                (let [descriptor { :name name :fields (apply merge (flatten fields))}
                      res (gql.GraphQLObjectType. (clj->js descriptor))]
                  (common/dbg-print "Created GraphQLObjectType: %s" descriptor) res))]
              (let [types (set/difference (set (keys @type-map)) (set (keys primitive-types)) (set (keys @enums)))]
                (common/dbg-print "Created union input type: %s" union-input-type-desc)
                (gql.GraphQLSchema. (clj->js {
                  :query (create-obj-type "RootQuery" (map get-query-descriptors types))
                  :mutation (create-obj-type "RootMutation" (map get-mutations (set/difference types (set @unions))))}))))))))))
