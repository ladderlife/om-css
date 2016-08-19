(ns om-css.devcards.sablono
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]
                   [cljs.test :refer [is testing async]])
  (:require [devcards-om-next.core :as don :refer-macros [defcard-om-next]]
            [goog.dom :as gdom]
            [om.next :as om]
            [om.dom :as om-dom]
            [om-css.dom :as dom]
            [om-css.core :as oc :refer-macros [defui defcomponent]]
            [sablono.core :as sab :refer [html]]))

(defui ^:once SablonoDefui
  static oc/Style
  (style [_]
    [[:.root {:background-color "tomato"}]
     [:.section {:background-color :green}]])
  Object
  (render [this]
    (html
      [:div {:class :root} "div with class :root"
       [:section {:class :section} "section with class :section"]])))

(defcard-om-next sablono-defui
  SablonoDefui)

(defcomponent SablonoDefcomponent [props children]
  [[:.root {:background-color "tomato"}]
   [:.section {:background-color :green}]]
  (html
    [:div {:class :root} "div with class :root"
     [:section {:class :section} "section with class :section"]]))

(defcard omcss-12-defcomponent-card
   SablonoDefcomponent)
