(ns om-css.devcards.core
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]
                   [cljs.test :refer [is testing async]])
  (:require [devcards-om-next.core :as don :refer-macros [defcard-om-next]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [om-css.core :as oc]))

(defui Foo
  static oc/Style
  (style [_]
    [:root {:color "#FFFFF"}
     :section (merge {} ;;css/default-section
                     {:background-color :green})])
  Object
  (componentDidMount [this _ _]
    (println "asd"))
  (componentWillMount [this]
    (println "oi"))
  (render [this]
    (dom/div {:id "ns-test"}
      (dom/div {:class :root}
        (dom/div {:className :section} "section")))))

(deftest namespaced-classnames-in-dom
  (testing "classnames are namespace qualified"
    (let [c (gdom/getElement "ns-test")]
      (is (not (nil? (gdom/getElementByClass "om_css_devcards_core_root"))))
      (is (not (nil? (gdom/getElementByClass "om_css_devcards_core_section")))))))

(defcard-om-next asd
  Foo)


