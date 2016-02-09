(ns om-css.devcards.core
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]
                   [cljs.test :refer [is testing async]])
  (:require [devcards-om-next.core :as don :refer-macros [defcard-om-next]]
            [goog.dom :as gdom]
            [om.next :as om]
            [om-css.dom :as dom]
            [om-css.core :as oc :refer-macros [defui defcomponent]]
            [om-css.devcards.bugs]))

(def style-1
  {:text-align :center})

(defui Foo
  static oc/Style
  (style [_]
    [[:.root {:background-color "tomato"}]
     [:.section (merge {} ;;style-1
                  {:background-color :green})]])
  Object
  (render [this]
    (dom/div {:id "ns-test"}
      (dom/div {:class :root} "div with class :root"
        (dom/section {:class :section} "section with class :section"
          (dom/p {:className "preserved"
                  :style {:background-color "turquoise"}} "paragraph with class \"preserved\""))))))

(defcard-om-next foo-card
  Foo)

(deftest namespaced-classnames-in-dom
  (testing "classnames are namespace qualified"
    (is (not (nil? (gdom/getElement "ns-test"))))
    (is (not (nil? (gdom/getElementByClass "om_css_devcards_core_Foo_root"))))
    (is (not (nil? (gdom/getElementByClass "om_css_devcards_core_Foo_section"))))
    (is (not (nil? (gdom/getElementByClass "preserved"))))))

(defui Bar
  oc/Style
  (style [_]
    [[:.bar {:margin "0 auto"}]
     [:.other {:padding "0"}]])
  Object
  (render [this]
    (dom/div {:class :bar} "Bar component")))

(defcard-om-next bar-card
  Bar)

(defui ComponentWithoutStyle
  Object
  (render [this]
    (dom/div {:id "component-no-style"}
      "Component Without Style.")))

(defcard-om-next card-component-no-style
  ComponentWithoutStyle)

(defui NotStaticStyleComponent
  oc/Style
  (style [this]
    {:.somestyle {:background-color "red"}})
  Object
  (render [this]
    (dom/div {:class :somestyle}
      "Component that implements Style (non-static)")))

(defcard-om-next card-component-no-static-style
  "Test that Style doesn't need to appear with `static`"
  NotStaticStyleComponent)

(defcomponent defcomponent-example [props children]
  (dom/div {:class :defcomponent-class}
    "`defcomponent` example with class `:defcomponent-class`"))

(defcard defcomponent-example-card
  (defcomponent-example))

(defcomponent nested-defcomponent-example [props children]
  (dom/div {:id "nested-defcomponent" :class :nested-defcomponent}
    "Nested `defcomponent` example"
    (defcomponent-example {:class :some}
      "some text")))

(defcard nested-defcomponent-example-card
  (nested-defcomponent-example))

(defcomponent defcomponent-with-style [props children]
  [[:.example-class {:background-color "tomato"}]]
  (dom/div {:class :example-class}
    "Nested `defcomponent` example"
    (defcomponent-example {:class :some}
      "some text")))

(defcard defcomponent-with-style-card
  (defcomponent-with-style))

(deftest namespaced-classnames-in-defcomponent
  (testing "`defcomponent` with styles"
    (is (not (nil? (gdom/getElementByClass "om_css_devcards_core_defcomponent-example_defcomponent-class"))))
    (is (not (nil? (gdom/getElementByClass "om_css_devcards_core_defcomponent-with-style_example-class"))))))

(defui MultipleClassesDefui
  static oc/Style
  (style [_]
    [[:.some {:background-color "tomato"}]
     [:.other {:color "yellow"}]])
  Object
  (render [this]
    (dom/div {:id "multiple-classes-test-defui"
              :class [:some :other]}
      "div with classes [:some :other]")))

(defcard-om-next defui-multiple-classes
  "Render a `defui` component with multiple classes"
  MultipleClassesDefui)

(deftest multiple-classnames-in-defui
  (testing "`defcomponent` with styles"
    (let [c (gdom/getElement "multiple-classes-test-defui")
          cns (.-className c)
          cns (.split cns " ")]
      (is (not (nil? c)))
      (is (= (count cns) 2))
      (is (= (first cns) "om_css_devcards_core_MultipleClassesDefui_some"))
      (is (= (second cns) "om_css_devcards_core_MultipleClassesDefui_other")))))

(defcomponent MultipleClassesDefcomponent [props children]
  [[:.some {:background-color "tomato"}]
   [:.other {:color "yellow"}]]
  (dom/div {:id "multiple-classes-test-defcomponent"
            :class [:some :other]}
    "div with classes [:some :other]"))

(defcard defcomponent-multiple-classes
  "Render a `defcomponent` component with multiple classes"
  (js/React.createElement MultipleClassesDefcomponent))
 
(deftest multiple-classnames-in-defcomponent
  (testing "`defcomponent` with styles"
    (let [c (gdom/getElement "multiple-classes-test-defcomponent")
          cns (.-className c)
          cns (.split cns " ")]
      (is (not (nil? c)))
      (is (= (count cns) 2))
      (is (= (first cns) "om_css_devcards_core_MultipleClassesDefcomponent_some"))
      (is (= (second cns) "om_css_devcards_core_MultipleClassesDefcomponent_other")))))
