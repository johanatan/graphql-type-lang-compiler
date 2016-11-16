;; Copyright (c) 2015 Jonathan L. Leonard

(ns graphql-tlc.consumer
  (:require [graphql-tlc.common :as common]
            [graphql-tlc.schema :as schema]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [clojure.string]
            [cljs.nodejs :as node]))

(def ^:private gql (node/require "graphql"))
(def ^:private flat (node/require "flat"))

(defprotocol DataResolver
  (query [this typename predicate])
  (create [this typename inputs])
  (modify [this typename inputs])
  (delete [this typename id]))

(def ^:private primitive-types {
  "ID"      gql.GraphQLID
  "Boolean" gql.GraphQLBoolean
  "String"  gql.GraphQLString
  "Float"   gql.GraphQLFloat
  "Int"     gql.GraphQLInt })

(defn GraphQLConsumer [data-resolver]
  (let [type-map (atom primitive-types)
        inputs-map (atom {})
        fields-map (atom {})
        unions (atom [])
        enums (atom {})]
    (letfn [
      (get-by-id [typename id]
        (query data-resolver typename (common/format "id=%s" (js->clj id))))
      (get-by-fields [typename fields]
        (let [msg (clojure.string/join "&" (map #(common/format "%s=%s" %1 (js->clj (aget fields %1)))
                                                (common/jskeys fields)))]
          (query data-resolver typename msg)))
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
      (get-input-type [typ is-list? is-not-null?] (if (contains? (set (keys @inputs-map)) typ)
                                                    (@inputs-map typ)
                                                    (get-type typ is-list? is-not-null?)))
      (get-resolver [datatype is-list? fieldname]
        (if (not (is-primitive? datatype))
          {:resolve (fn [parent]
                      (if is-list?
                        (clj->js (map (fn [id] (get-by-id datatype id)) (aget parent fieldname)))
                        (get-by-id datatype (aget parent fieldname))))}))
      (get-field-spec [[fieldname datatype is-list? is-not-null?]]
        (let [typ (get-type datatype is-list? is-not-null?)
              resolver (get-resolver datatype is-list? fieldname)
              res {fieldname (merge {:type typ} resolver)}] res))
      (convert-to-input-object-field [field]
        (let [wrappers (atom '())
              field-type (atom nil)]
          (loop [ft (.-type field)]
            (if (.-ofType ft)
              (do
                (if (not= (type ft) gql.GraphQLNonNull) ; all fields in our input type are optional
                  (swap! wrappers conj (.-constructor ft)))
                (recur (.-ofType ft)))
              (reset! field-type ft)))
          (if (not (gql.isInputType @field-type))
            (reset! field-type (create-input-object @field-type)))
          (clj->js {:type (reduce (fn [typ clas] (clas. typ)) @field-type @wrappers)})))
      (input-object-typename [typename] (common/format "%sInput" typename))
      (create-input-object [object-type]
        (gql.GraphQLInputObjectType. (clj->js {:name (input-object-typename (.-name object-type))
                                               :fields (into {} (for [[k v] (js->clj (.getFields object-type))]
                                                                  [k (convert-to-input-object-field (clj->js v))]))})))]
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
              (common/format "Type must contain an 'id' field. Fields: %s"
                (common/pprint-str fieldnames)))
            (swap! type-map assoc typename res)
            (swap! fields-map assoc fieldnames [typename (map rest field-descriptors)])
            (console/log (common/format "Created object type: %s" typename))
            (swap! inputs-map assoc typename (create-input-object res))
            (console/log (common/format "Create input object type: %s for type: %s" (input-object-typename typename) typename))))
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
          (let [union-input-type-desc { :name "Union"
                                        :fields { :type { :type (gql.GraphQLNonNull. gql.GraphQLString)}
                                                  :id { :type (gql.GraphQLNonNull. gql.GraphQLID)}}}
                union-input-type (gql.GraphQLInputObjectType. (clj->js union-input-type-desc))]
            (letfn [
              (get-query-descriptors [typ]
                (let [[field-names field-meta] (first (filter (fn [[k v]] (= (v 0) typ)) @fields-map))
                      zipped (map vector field-names (second field-meta))
                      with-types (map (fn [[k v]] [k (get-input-type (nth v 0) (nth v 1) false)]) zipped)
                      args (apply merge (map #(do {(keyword (%1 0)) {:type (%1 1)}}) with-types))
                      res
                        [{typ
                          {:type (gql.GraphQLList. (get @type-map typ))
                           :args args
                           :resolve (fn [root obj] (clj->js (get-by-fields typ (flat obj))))}}
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

(defn- bail [msg] (fn [& _] (throw (js/Error. (common/format "Not implemented: '%s'." msg)))))

(defn get-data-resolver [is-js? {:keys [query create modify delete]
                                 :or   {query (bail "query")
                                        create (bail "create")
                                        modify (bail "modify")
                                        delete (bail "delete")}}]
  (reify DataResolver
    (query [_ typename predicate] (query typename predicate))
    (create [_ typename inputs] (create typename (if is-js? (clj->js inputs) inputs)))
    (modify [_ typename inputs] (modify typename (if is-js? (clj->js inputs) inputs)))
    (delete [_ typename id] (delete typename id))))

(defn get-schema [resolver-methods schema-filename-or-contents]
  (let [is-js? (object? resolver-methods)]
    (first (second (schema/load-schema schema-filename-or-contents
    (GraphQLConsumer (get-data-resolver is-js?
                       (if is-js?
                         (walk/keywordize-keys (js->clj resolver-methods))
                         resolver-methods))))))))

(defn noop [] nil)

(set! *main-cli-fn* noop)
