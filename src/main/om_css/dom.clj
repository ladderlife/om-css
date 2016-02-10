(ns om-css.dom
  (:refer-clojure :exclude [map meta time])
  (:require [om.dom :as dom]))

;;; generate all form tags

(def form-tags '[input textarea option])

(def all-tags (concat dom/tags form-tags))

(defmacro gen-tag-fns
  []
  `(do
     ~@(clojure.core/map
         (fn [tag]
           `(defn ~tag [& ~'params]
              (apply render-elem ~(symbol "om.dom" (name tag)) ~'params)))
         all-tags)))
