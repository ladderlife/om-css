(ns om-css.devcards.bugs
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]
                   [cljs.test :refer [is testing async]])
  (:require [devcards-om-next.core :as don :refer-macros [defcard-om-next]]
            [goog.dom :as gdom]
            [om.next :as om]
            [om.dom :as om-dom]
            [om-css.dom :as dom]
            [om-css.core :as oc :refer-macros [defui defcomponent]]
            [om-css.devcards.constants :as c :refer [other-style]]))

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

;;====================
;; OMCSS-12

(defui ^:once OMCSS-12-Defui
   static oc/Style
   (style [_]
     [[:.root {:background-color "tomato"}]
      [:.section (merge c/some-style
                   {:background-color :green})]])
   Object
   (render [this]
     (dom/div {:class :root} "div with class :root"
       (dom/section {:class :section} "section with class :section"))))

(defcard-om-next omcss-12-defui-card
   OMCSS-12-Defui)

(defcomponent OMCSS-12-Defcomponent [props children]
  [[:.root {:background-color "tomato"}]
   [:.section (merge c/some-style other-style
                {:background-color :green})]]
  (dom/div {:class :root} "div with class :root"
    (dom/section {:class :section} "section with class :section")))

(defcard omcss-12-defcomponent-card
   OMCSS-12-Defcomponent)


;;====================
;; OMCSS-18

(defn omcss-18-add-classes [{:keys [class] :as props} & classes]
  (merge props {:class (flatten [class classes])}))

(defcomponent omcss-18-component [{:keys [class] :as props} children]
  [[:.test {:color :test}]]
  (dom/div (omcss-18-add-classes props :test) children))

(defcard omcss-18-card
  (omcss-18-component {:id "omcss-18"} "test"))

(deftest omcss-18-test
  (let [c (gdom/getElement "omcss-18")
          cns (.-className c)
          cns (.split cns " ")]
      (is (not (nil? c)))
      (is (not (nil?
                 (some
                   #{"om_css_devcards_bugs_omcss-18-component_test"}
                   cns))))))

;;====================
;; OMCSS-20

(defcomponent omcss-20-component [props children]
  (let [dir "even"]
    (dom/div
      {:class (if (= dir "even") [:even] [])}
      "asd")))

(defcard omcss-20-card
  (omcss-20-component))

;;====================
;; OMCSS-23

(defcomponent inner [props children]
  (dom/div props children))

(defcomponent outer [props children]
  (dom/div props children))

(defcomponent wrapper [props children]
  [[:.inner {}]]
  (let []
    (outer {:class :outer
            :id "omcss-23"}
     (inner {:class :inner} "inner"))))

(defcard omcss-23-card
  (wrapper))

(deftest omcss-23-test
  (let [c (gdom/getElement "omcss-23")
          cns (.-className c)
          cns (.split cns " ")]
      (is (not (nil? c)))
      (is (not (nil? (some #{":outer"} cns))))))

;;====================
;; OMCSS-22

(defcomponent omcss-22 [props children]
  []
  [(dom/div nil "something") (dom/div "other")])

(defcard omcss-22-card
  (dom/div nil
    (omcss-22)))

;;====================
;; OMCSS-25

(defcomponent omcss-25-component [props children]
  (dom/div {:class-name nil} "omcss-25"))

(defcard omcss-25-card
  (omcss-25-component))

;;====================
;; nested-fns in components

(defcomponent nested-fn-component [_ _]
  [[:hi {:text-align "center"}]]
  (dom/div nil
    "something"
    (map-indexed
      (fn [index _]
        (dom/p {:class :hi} (str "index: " index)))
      [1 2 3 4])))

(defcard nested-fn-card
  (nested-fn-component))

;;====================
;; OMCSS-32

(defui OMCSS-32-Component
  Object
  (render [this]
    (dom/div {:ref :some-ref} "div with ref")))

(def omcss-32-reconciler
  (om/reconciler {:state (atom nil)
                  :parser (om/parser {:read #(do {})})}))

(defcard-om-next omcss-32-card
  OMCSS-32-Component
  omcss-32-reconciler)

(deftest test-omcss-32
  (let [c (om/class->any omcss-32-reconciler OMCSS-32-Component)]
    (is (some? c))
    (is (some? (om/react-ref c :some-ref)))))

;;====================
;; Test that #js {} as props works

(defui JSObjsComponent
  Object
  (render [this]
    (dom/div #js {:id "js-obj-comp"} "I work")))

(defcard-om-next js-objs-card
  JSObjsComponent)


;;====================
;; no protocol method -assoc for [object Object]

(defcomponent no-protocol-defc [props children]
  (dom/div props children))

(defcard no-protocol-card
  (no-protocol-defc
    (dom/div {:id "no-protocol-comp"} "some text")))

(deftest test-no-protocol-for-object
  (let [c (gdom/getElement "no-protocol-comp")]
    (is (some? c))))
