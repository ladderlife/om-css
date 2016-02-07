(ns om-css.dom
  (:refer-clojure :exclude [map meta time])
  (:require [om.dom :as dom]))

;;; defcomponent

(defmacro defcomponent
  [component-name [props children] & body]
  "Example usage:
  (defcomponent foo
    [props children]
    (dom/div {:class \"foo\"}
             children))

  (foo (dom/a {:href \"http://google.com\"}))
  "
  (list 'defn component-name '[& params]
        (concat (list 'let [[props (or children '_)] `(parse-params ~'params)])
                body)))

;;; generate all form tags

(def form-tags '[input textarea option])

(def all-tags (concat dom/tags form-tags))

(defmacro gen-tag-fns
  []
  `(do
     ~@(clojure.core/map
         (fn [tag]
           `(defn ~tag [~'this & ~'params]
              (apply render-elem ~(symbol "om.dom" (name tag)) ~'this ~'params)))
         all-tags)))
