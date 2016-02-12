(ns om-css.devcards.bugs
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]
                   [cljs.test :refer [is testing async]])
  (:require [devcards-om-next.core :as don :refer-macros [defcard-om-next]]
            [goog.dom :as gdom]
            [om.next :as om]
            [om.dom :as om-dom]
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

;;====================
;; OMCSS-7

(defcomponent omcss-7-component-1
  [{:keys [class] :as props} children]
  [:.root {:color :purple}]
  (dom/div
    {:id "omcss-7" :class (into class [:root])}
    "class is now [:root :root], parent's class is lost"))

(defcomponent omcss-7component-2
  [props children]
  [:.root {:text-decoration :underline}]
  (omcss-7-component-1 {:class [:root]}))


(defcard omcss-7-card
  "Test that assigning classes to child components works"
  (omcss-7component-2))

(deftest omcss-7-test
  (let [c (gdom/getElement "omcss-7")
          cns (.-className c)
          cns (.split cns " ")]
      (is (not (nil? c)))
      (is (not (nil?
                 (some
                   #{"om_css_devcards_bugs_omcss-7component-2_root"}
                   cns))))
      (is (not (nil?
                 (some
                   #{"om_css_devcards_bugs_omcss-7-component-1_root"}
                   cns))))))

;;====================
;; OMCSS-8

(defcomponent omcss-8-component [props children]
  (om-dom/div #js {:className "test"} "test"))

(defcard omcss-8-card
  (omcss-8-component))

;;====================
;; OMCSS-11

(defcomponent omcss-11-component [props children]
  [[:.root {:color :purple}]]
  (dom/div
    (merge props {:class :root})
    "purple"))

(defcard omcss-11-card
  (omcss-11-component))
