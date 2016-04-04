(ns om-css.utils
  (:require [clojure.string :as string]))

#?(:clj
   (defn- cljs-env? [env]
     (boolean (:ns env))))

#?(:clj
   (defmacro if-cljs
     "Return `then` if we are generating cljs code and `else` for Clojure code."
     [then else]
     (if (cljs-env? &env) then else)))


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


(defn format-unevaluated-class-names
  ([cns component-info]
   (format-unevaluated-class-names cns component-info true))
  ([cns component-info classes-seen]
   ;; unevaluated data structures: a list might be a function call, we
   ;; only support strings, vectors or keywords
   (cond
     (or (vector? cns)
       (string? cns)
       (keyword? cns))
     (let [cns' (map #(cond-> %
                        (and classes-seen
                          (or (true? classes-seen)
                            (get classes-seen %)))
                        (format-class-name component-info))
                  (if (sequential? cns) cns [cns]))]
       (if (sequential? cns)
         (into [] cns')
         (first cns')))

     (map? cns)
     (into {} (map (fn [[k v]]
                     [(format-unevaluated-class-names k component-info classes-seen)
                      (format-unevaluated-class-names v component-info classes-seen)])) cns)

     (list? cns)
     (map #(format-unevaluated-class-names % component-info classes-seen) cns)

     :else cns)))

;; only transform keywords at runtime, vectors and strings have
;; already been prefixed at macro-expansion time
(defn format-dom-class-names [cns component-info classes-seen]
  (->> (if (sequential? cns) cns [cns])
    (map #(cond-> %
            (keyword? %) name
            (get classes-seen %) (format-class-name component-info)))
    (string/join " ")))
