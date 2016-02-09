(ns om-css.devcards.bugs
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]
                   [cljs.test :refer [is testing async]])
  (:require [devcards-om-next.core :as don :refer-macros [defcard-om-next]]
            [goog.dom :as gdom]
            [om.next :as om]
            [om-css.dom :as dom]
            [om-css.core :as oc :refer-macros [defui defcomponent]]))

;;====================
;; OMCSS-2

(defcomponent omcss-2-component [{:keys [a b] :as props} children]
  (dom/div nil
    (dom/p nil (str "prop a: " a))
    (dom/p nil (str "prop b: " b))))

(defcard omcss-2-card
  "Test that destructuring works in defcomponent's args"
  (omcss-2-component {:a 1 :b 2}))

;;====================
;; OMCSS-4

(defcomponent omcss-4-component
  [props children]
  [[:.root {:color :purple}]
   [:.active {:font-weight :bold}]
   [:.section {:color :green}]]
  (let [x true]
    (dom/div
     {:class [:root :active]}
     "div with class root"
     (dom/hr)
     (dom/section {:class :section}
                  "section with class :section"
                  children))))

(defcard omcss-4-card
  "Test that `let` expressions work in `defcomponent`"
  (omcss-4-component))

;;====================
;; OMCSS-3

(defcomponent omcss-3-component [props children]
  [[:.root {:color :red}]]
  (dom/div {:class :root :class-name "inline-block"}
    "test"))

(defcard omcss-3-card
  "Test that merging with regular class names works"
  (omcss-3-component))

;;====================
;; OMCSS-5

(defcomponent omcss-5-component [props children]
  [:$desktop
   [:.root {:background-color "tomato"}]]
  (dom/div {:className "desktop"}
    (dom/div {:class :root}
      "test")))

(defcard omcss-5-card
  "Test that referencing global class names works"
  (omcss-5-component))
