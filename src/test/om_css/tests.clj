(ns om-css.tests
  (:require [clojure.test :refer [deftest testing is are]]
            [om-css.core :as oc]
            [om-css.utils :as utils]))

(def component-info
  {:component-name "Foo"
   :ns-name "ns.core"})

(deftest test-reshape-render
  (testing "`reshape-render` adds ns & component info to props"
    (let [unchanged '((dom/div nil "text"))]
      (is (= (oc/reshape-render
               '((dom/div {}
                    "Nested `defcomponent` example"))
               component-info)
            '((dom/div {:omcss$info {:component-name "Foo"
                                      :ns-name "ns.core"}}
                 "Nested `defcomponent` example"))))
      (is (= (oc/reshape-render unchanged component-info) unchanged))))
  (testing "`reshape-render` adds namespace qualified classes (:class)"
    (is (= (oc/reshape-render
             '((dom/div {:class :bar} "bar"))
             component-info)
          '((dom/div {:omcss$info {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class "ns_core_Foo_bar"} "bar"))))
    (is (= (oc/reshape-render
             '((dom/div {:class :bar} "bar"
                   (dom/p {:class :baz} "baz")))
             component-info)
          '((dom/div {:omcss$info {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class "ns_core_Foo_bar"} "bar"
              (dom/p {:omcss$info {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class "ns_core_Foo_baz"} "baz"))))))
  (testing "`reshape-render` preserves `:className` classnames"
    (is (= (oc/reshape-render
             '((dom/div {:className "bar"} "bar"))
             component-info)
          '((dom/div {:omcss$info {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :className "bar"} "bar")))))
  (testing "`reshape-render` preserves `:class`'s data structure"
    (is (= (oc/reshape-render
             '((dom/div {:class [:root]}))
              component-info)
          '((dom/div {:omcss$info {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class ["ns_core_Foo_root"]})))))
  (testing "`reshape-render` skips `let` bindings"
    (let [form '((let [x true]
                    (dom/div
                      {:class [:root :active]}
                      "div with class root"
                      (dom/hr)
                      (dom/section {:class :section}
                        "section with class :section"
                        children))))]
      (is (= (oc/reshape-render form component-info)
            '((let [x true]
                 (dom/div
                   {:class ["ns_core_Foo_root" "ns_core_Foo_active"]
                    :omcss$info {:component-name "Foo"
                                 :ns-name "ns.core"}}
                   "div with class root"
                   (dom/hr)
                   (dom/section {:class "ns_core_Foo_section"
                                 :omcss$info {:component-name "Foo"
                                              :ns-name "ns.core"}}
                     "section with class :section"
                     children)))))))))

(deftest test-get-style
  (let [form '(static om/IQuery
               (query [this])
               static oc/Style
               (style [_]
                 [:root {:color "#FFFFF"}
                  :section {:background-color :green}])
               static om/Ident
               (ident [this])
               Object
               (render [this])
               static om/IQueryParams
               (params [this]))]
    (is (= (oc/get-style-form form)
          '(style [_]
            [:root {:color "#FFFFF"}
             :section {:background-color :green}])))
    (is (nil? (oc/get-style-form
                '(Object
                   (render [this])
                   static om/Ident
                   (ident [this])))))
    (is (= (oc/get-component-style form)
          [:root {:color "#FFFFF"}
           :section {:background-color :green}]))))

(deftest test-reshape-defui
  (let [form '(om/IQuery
                (query [this])
                om/Ident
                (ident [this])
                Object
                (componentWillMount [this])
                (render [dia]
                  (dom/div {:class :foo} (dom/div nil "3")))
                static field a 3
                static om/IQuery
                (query [this] [:a]))
        expected '[om/IQuery
                   (query [this])
                   om/Ident
                   (ident [this])
                   Object
                   (componentWillMount [this])
                   (render [dia]
                     (dom/div
                       {:class "ns_core_Foo_foo"
                        :omcss$info {:component-name "Foo"
                                     :ns-name "ns.core"}}
                       (dom/div nil "3")))
                   static field a 3
                   static om/IQuery
                   (query [this] [:a])]]
    (is (= (oc/reshape-defui form component-info)
          expected))
    (is (= (oc/reshape-defui
             '(Object (render [this] (dom/div nil "foo")))
             component-info)
          '[Object (render [this] (dom/div nil "foo"))]))))

(deftest test-infer-requires
  (let [env '{:ns {:name ns.core
                   :requires {c ns.constants
                              o ns.other}}}]
    (are [forms res] (= (oc/infer-requires env forms))
      '[[:.root {:background-color "tomato"}]
        [:.section (merge c/style-1 {:background-color :green})]]
      '[(require '[ns.constants :as c])]

      '[[:.root (merge o/style-2 {:background-color "tomato"})]
        [:.section (merge c/style-1 {:background-color :green})]]
      '[(require '[ns.other :as o])
            (require '[ns.constants :as c])]

      '[:$desktop
        [:.root (merge c/style-1 {:background-color "tomato"})]]
      '[(require '[ns.constants :as c])])))

(deftest omcss-11
  (let [form1 '((dom/div (merge props {:class :root})
                  "purple"))
        form2 '((dom/div (merge {:class :root} props) "purple"))]
    (is (= (oc/reshape-render form1 component-info)
          '((dom/div (merge props {:omcss$info {:component-name "Foo"
                                                :ns-name "ns.core"}
                                   :class "ns_core_Foo_root"})
              "purple"))))
    (is (= (oc/reshape-render form2 component-info)
          '((dom/div (merge {:omcss$info {:component-name "Foo"
                                          :ns-name "ns.core"}
                             :class "ns_core_Foo_root"}
                       props)
              "purple"))))))

(deftest test-reshape-props
  (are [props res] (= (oc/reshape-props props component-info) res)
    '(merge {:class "foo"}) '(merge {:omcss$info {:ns-name "ns.core"
                                                  :component-name "Foo"}
                                     :class "ns_core_Foo_foo"})
    {:class "foo"} {:omcss$info {:ns-name "ns.core"
                                 :component-name "Foo"}
                    :class "ns_core_Foo_foo"}
    {:class :foo} {:omcss$info {:ns-name "ns.core"
                                :component-name "Foo"}
                   :class "ns_core_Foo_foo"}
    ;; TODO: is this intended behavior?
    '(merge {:class (subs (str :foo) 1)})'(merge {:omcss$info {:ns-name "ns.core"
                                                               :component-name "Foo"}
                                                  :class (subs (str :foo) 1)})))

(deftest test-format-class-names
  (are [cns res] (= (utils/format-class-names component-info cns) res)
    :foo "ns_core_Foo_foo"
    "foo" "ns_core_Foo_foo"
    [:foo] ["ns_core_Foo_foo"]
    ["foo"] ["ns_core_Foo_foo"]
    ["foo" :bar] ["ns_core_Foo_foo" "ns_core_Foo_bar"]))
