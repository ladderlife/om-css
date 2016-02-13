(ns om-css.utils
  (:require [clojure.string :as string]))

(defn format-class-name [component-info class-name]
  "generate namespace qualified classname"
  (let [ns-name (:ns-name component-info)
        class-name (name class-name)
        component-name (-> (:component-name component-info)
                         (string/split #"/")
                         last)]
    (str (string/replace (munge ns-name) #"\." "_")
      "_" component-name "_" class-name)))


(defn format-cns* [component-info cns]
  ;; unevaluated data structures: a list might be a function call, we
  ;; only support strings, vectors or keywords
  #?(:clj (if-not (or (vector? cns)
                    (string? cns)
                    (keyword? cns))
            cns
            (let [cns' (map #(format-class-name component-info %)
                         (if (sequential? cns) cns [cns]))]
              (if (sequential? cns)
                (into [] cns')
                (first cns'))))
     ;; only transform keywords at runtime, vectors and strings have
     ;; already been prefixed at macro-expansion time
     :cljs (->> (if (sequential? cns) cns [cns])
             (map #(cond->> %
                     (keyword? %) (format-class-name component-info)))
             (string/join " "))))

(defn format-class-names [component-info cns]
  (format-cns* component-info cns))
