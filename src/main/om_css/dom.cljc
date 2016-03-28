(ns om-css.dom
  (:refer-clojure :exclude [map meta time mask use])
  #?(:cljs (:require-macros [om-css.dom :refer [gen-tag-fns]]))
  (:require #?(:clj  [cellophane.dom :as dom]
               :cljs [om.dom :as dom])
            [clojure.string :as string]
            [om-css.utils :as utils #?@(:clj [:refer [if-cljs]])]))

;;; generate all form tags

#?(:clj
   (def form-tags '[input textarea option select]))

#?(:clj
   (def all-tags
     ;; cellophane has these tags
     (cond-> dom/tags
       (not (some (set form-tags) dom/tags)) (concat form-tags))))

#?(:clj
   (defmacro gen-tag-fns
     []
     `(do
        ~@(clojure.core/map
            (fn [tag]
              `(defn ~tag [& ~'params]
                 (if-cljs
                   (apply render-element ~(symbol "om.dom" (name tag)) ~'params)
                   (apply render-element ~(symbol "cellophane.dom" (name tag)) ~'params))))
            all-tags))))

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
  "Leaves :className unchanged, formats :class accordingly. Converts :ref to string."
  (let [map #?(:clj  clojure.core/map
               :cljs cljs.core/map)]
    (->> attrs
      (map
        (fn [[k v]]
          [(format-opt-key k)
           (condp = k
             :class
             (let [component-info (:omcss$info attrs)
                   classes-seen (:classes component-info)]
               (utils/format-dom-class-names v component-info classes-seen))

             :ref
             (str v)

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
                        (str (m k "")
                          (str " "
                            (some-> v
                              (string/replace #":omcss\$info" "")))))))
                  (cond-> m
                    (not= k :omcss$info) (assoc k v)))) {}))))

(defn format-opts
  "Returns JavaScript object for React DOM attributes from opts map"
  [opts]
  (if (map? opts)
    (-> opts
      format-attrs
      #?(:cljs clj->js))
    opts))

(defn parse-params
  [params]
  (let [props (first params)]
    (update
      (if #?(:clj (and (map? props)
                       (not (record? props)))
             :cljs (or (and (cljs.core/object? props)
                            (not (aget props "$$typeof"))
                            (not= (goog/typeOf (aget props "$$typeof")) "symbol"))
                       (map? props)))
        [props (rest params)]
        [nil params])
      1 flatten)))

(defn render-element
  [render & params]
  (let [[attrs children] (parse-params params)]
    (apply render (format-opts attrs) children)))

(gen-tag-fns)

;;; proxy thru to om.dom
#?(:cljs
   (defn render
     [& params]
     (apply dom/render params)))

(defn render-to-str
   [& params]
   (apply dom/render-to-str params))

(defn node
  [& params]
  (apply dom/node params))
