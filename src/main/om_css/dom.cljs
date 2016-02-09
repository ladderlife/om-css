(ns om-css.dom
  (:refer-clojure :exclude [map meta time mask])
  (:require-macros [om-css.dom :refer [gen-tag-fns]])
  (:require [om.dom :as dom]
            [clojure.string :as string]))

;;; lifted from https://github.com/plumatic/om-tools/blob/master/src/om_tools/dom.cljx

(defn camel-case
  "Converts kebab-case to camelCase"
  [s]
  (string/replace s #"-(\w)" (comp string/upper-case second)))

(defn- opt-key-case
  "Converts attributes that are kebab-case and should be camelCase"
  [attr]
  (if (or (< (count attr) 5)
          (case (subs attr 0 5) ("data-" "aria-") true false))
    attr
    (camel-case attr)))

(defn- opt-key-alias
  "Converts aliased attributes"
  [opt]
  (case opt
    :class :className
    :for :htmlFor
    opt))

(defn format-opt-key
  "Returns potentially formatted name for DOM element attribute.
   Converts kebab-case to camelCase."
  [opt-key]
  (-> opt-key
      opt-key-alias
      name
      opt-key-case
      keyword))

(declare format-opts)

(defn format-opt-val
  "Returns potentially modified value for DOM element attribute.
   Recursively formats map values (ie :style attribute)"
  [opt-val]
  (cond
    (map? opt-val) (format-opts opt-val)
    :else opt-val))

(defn- format-class-name [this-arg class-name]
  "generate namespace qualified classname"
  (let [ns-name (:ns-name this-arg)
        class-name (name class-name)
        component-name (-> (:component-name this-arg)
                         (string/split #"/")
                         last)]
    (str (string/replace (munge ns-name) #"\." "_")
      "_" component-name "_" class-name)))

(defn format-class-names [this-arg cns]
  (string/join " "
    (cljs.core/map #(cond->> %
                      (keyword? %) (format-class-name this-arg))
      (if (sequential? cns) cns [cns]))))

(defn- format-attrs [this-arg attrs]
  "leaves :className unchanged, formats :class accordingly"
  (->> attrs
    (cljs.core/map
      (fn [[k v]]
        [(format-opt-key k)
         (if (= k :class)
           (format-class-names this-arg v)
           (format-opt-val v))]))
    (reduce (fn [m [k v]]
              (if (= k :className)
                (assoc m k (str (m k "") (str " " v)))
                (assoc m k v))) {})))

(defn format-opts
  "Returns JavaScript object for React DOM attributes from opts map"
  [this-arg opts]
  (if (map? opts)
    (->> opts
      (format-attrs this-arg)
      clj->js)
    opts))

(defn parse-params
  [[this-arg & params]]
  (let [params' (update
                  (if (map? (first params))
                    [(first params) (rest params)]
                    [nil params])
                  1 flatten)]
    (into [this-arg] params')))

(defn render-elem
  [render & params]
  (let [[this-arg attrs children] (parse-params params)]
    (apply render (format-opts this-arg attrs) children)))

(gen-tag-fns)

;;; proxy thru to om.dom
(defn render
   [& params]
   (apply dom/render params))

(defn render-to-str
   [& params]
   (apply dom/render-to-str params))

(defn node
   [& params]
   (apply dom/node params))
