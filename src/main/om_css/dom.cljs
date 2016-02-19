(ns om-css.dom
  (:refer-clojure :exclude [map meta time mask])
  (:require-macros [om-css.dom :refer [gen-tag-fns]])
  (:require [clojure.string :as string]
            [om.dom :as dom]
            [om-css.utils :as utils]))

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

(defn- format-attrs [attrs]
  "leaves :className unchanged, formats :class accordingly"
  (->> attrs
    (cljs.core/map
      (fn [[k v]]
        [(format-opt-key k)
         (if (= k :class)
           ;; TODO: still formats every class. could also filter based
           ;; on the classes that we've seen in the styles vector.
           (utils/format-class-names (:omcss$info attrs) v)
           (format-opt-val v))]))
    (reduce (fn [m [k v]]
              (if (= k :className)
                ;; :omcss$info might end up in classes because we're naively
                ;; adding it to a map that appears in props. A stronger
                ;; solution might be to check if such map contains the :class keyword
                ;; but this might introduce other edge cases. Circle back.
                (let [v' (remove #{:omcss$info "omcss$info"} v)]
                  (assoc m k
                    (string/trim
                      (str (m k "") (str " " (string/replace v #":omcss\$info" ""))))))
                (cond-> m
                  (not= k :omcss$info) (assoc k v)))) {})))

(defn format-opts
  "Returns JavaScript object for React DOM attributes from opts map"
  [opts]
  (if (map? opts)
    (->> opts
      format-attrs
      clj->js)
    opts))

(defn parse-params
  [params]
  (update
    (if (map? (first params))
      [(first params) (rest params)]
      [nil params])
    1 flatten))

(defn render-elem
  [render & params]
  (let [[attrs children] (parse-params params)]
    (apply render (format-opts attrs) children)))

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
