(ns om-css.tests
  (:require [clojure.test :refer [deftest testing is are]]
            [om-css.core :as oc]))

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
            '((dom/div {:omcss$this {:component-name "Foo"
                                      :ns-name "ns.core"}}
                 "Nested `defcomponent` example"))))
      (is (= (oc/reshape-render unchanged component-info) unchanged))))
  (testing "`reshape-render` adds namespace qualified classes (:class)"
    (is (= (oc/reshape-render
             '((dom/div {:class :bar} "bar"))
             component-info)
          '((dom/div {:omcss$this {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class "ns_core_Foo_bar"} "bar"))))
    (is (= (oc/reshape-render
             '((dom/div {:class :bar} "bar"
                   (dom/p {:class :baz} "baz")))
             component-info)
          '((dom/div {:omcss$this {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class "ns_core_Foo_bar"} "bar"
              (dom/p {:omcss$this {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class "ns_core_Foo_baz"} "baz"))))))
  (testing "`reshape-render` preserves `:className` classnames"
    (is (= (oc/reshape-render
             '((dom/div {:className "bar"} "bar"))
             component-info)
          '((dom/div {:omcss$this {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :className "bar"} "bar")))))
  (testing "`reshape-render` preserves `:class`'s data structure"
    (is (= (oc/reshape-render
             '((dom/div {:class [:root]}))
              component-info)
          '((dom/div {:omcss$this {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class ["ns_core_Foo_root"]}))))))

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
                        :omcss$this {:component-name "Foo"
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