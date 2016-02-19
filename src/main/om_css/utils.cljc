(ns om-css.utils
  (:require [clojure.string :as string]))

(defn format-class-name [component-info class-name]
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


(defn format-cns* [component-info cns classes-seen]
  ;; unevaluated data structures: a list might be a function call, we
  ;; only support strings, vectors or keywords
  #?(:clj (cond
            (or (vector? cns)
              (string? cns)
              (keyword? cns))
            (let [cns' (map #(cond->> %
                               (and classes-seen
                                 (if (fn? classes-seen)
                                   (classes-seen)
                                   (classes-seen %))) (format-class-name component-info))
                         (if (sequential? cns) cns [cns]))]
              (if (sequential? cns)
                (into [] cns')
                (first cns')))

            (list? cns)
            (map #(format-cns* component-info % classes-seen) cns)

            :else cns)
     ;; only transform keywords at runtime, vectors and strings have
     ;; already been prefixed at macro-expansion time
     :cljs (->> (if (sequential? cns) cns [cns])
             (map #(cond->> %
                     (keyword? %) (format-class-name component-info)))
             (string/join " "))))

(defn format-class-names
  ([component-info cns]
   (format-cns* component-info cns (constantly true)))
  ([component-info cns classes-seen]
   (format-cns* component-info cns classes-seen)))
