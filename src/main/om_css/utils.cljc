(ns om-css.utils
  (:require [clojure.string :as string]))

(defn format-class-name [class-name component-info]
  "generate namespace qualified classname"
  (if (symbol? class-name)
    class-name
    (let [ns-name (:ns-name component-info)
          class-name (name class-name)
          component-name (-> (:component-name component-info)
                           (string/split #"/")
                           last)]
      (str (string/replace (munge ns-name) #"\." "_")
        "_" component-name "_" class-name))))


(defn format-cns* [cns component-info classes-seen]
  ;; unevaluated data structures: a list might be a function call, we
  ;; only support strings, vectors or keywords
  #?(:clj (cond
            (or (vector? cns)
              (string? cns)
              (keyword? cns))
            (let [cns' (map #(cond-> %
                               (and classes-seen
                                 (if (true? classes-seen)
                                   classes-seen
                                   (get classes-seen %))) (format-class-name component-info))
                         (if (sequential? cns) cns [cns]))]
              (if (sequential? cns)
                (into [] cns')
                (first cns')))

            (map? cns)
            (into {} (map (fn [[k v]]
                   [(format-cns* k component-info classes-seen)
                    (format-cns* v component-info classes-seen)])) cns)

            (list? cns)
            (map #(format-cns* % component-info classes-seen) cns)

            :else cns)
     ;; only transform keywords at runtime, vectors and strings have
     ;; already been prefixed at macro-expansion time
     :cljs (->> (if (sequential? cns) cns [cns])
             (map #(cond-> %
                     (and (keyword? %)
                       (get classes-seen %)) (format-class-name component-info)))
             (string/join " "))))

(defn format-class-names
  ([cns component-info]
   (format-cns* cns component-info true))
  ([cns component-info classes-seen]
   (format-cns* cns component-info classes-seen)))
