(ns om-css.tests
  (:require [clojure.test :refer [deftest testing is are]]
            [om-css.core :as oc :refer [defui defcomponent]]
            [om-css.dom :as dom]
            [cellophane.next :as cellophane]
            [cellophane.dom :as cdom]
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
               component-info nil)
            '((dom/div {:omcss$info {:component-name "Foo"
                                      :ns-name "ns.core"}}
                 "Nested `defcomponent` example"))))
      (is (= (oc/reshape-render unchanged component-info nil) unchanged))))
  (testing "`reshape-render` adds namespace qualified classes (:class)"
    (is (= (oc/reshape-render
             '((dom/div {:class :bar} "bar"))
             component-info #{:bar})
          '((dom/div {:omcss$info {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class "ns_core_Foo_bar"} "bar"))))
    (is (= (oc/reshape-render
             '((dom/div {:class :bar} "bar"
                   (dom/p {:class :baz} "baz")))
             component-info #{:bar :baz})
          '((dom/div {:omcss$info {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class "ns_core_Foo_bar"} "bar"
              (dom/p {:omcss$info {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :class "ns_core_Foo_baz"} "baz"))))))
  (testing "`reshape-render` preserves `:className` classnames"
    (is (= (oc/reshape-render
             '((dom/div {:className "bar"} "bar"))
             component-info nil)
          '((dom/div {:omcss$info {:component-name "Foo"
                                   :ns-name "ns.core"}
                      :className "bar"} "bar")))))
  (testing "`reshape-render` preserves `:class`'s data structure"
    (is (= (oc/reshape-render
             '((dom/div {:class [:root]}))
              component-info #{:root})
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
      (is (= (oc/reshape-render form component-info #{:root :active :section})
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
    (is (= (oc/reshape-defui form component-info #{:foo})
          expected))
    (is (= (oc/reshape-defui
             '(Object (render [this] (dom/div nil "foo")))
             component-info nil)
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
    (is (= (oc/reshape-render form1 component-info #{:root})
          '((dom/div (merge props {:omcss$info {:component-name "Foo"
                                                :ns-name "ns.core"}
                                   :class "ns_core_Foo_root"})
              "purple"))))
    (is (= (oc/reshape-render form2 component-info #{:root})
          '((dom/div (merge {:omcss$info {:component-name "Foo"
                                          :ns-name "ns.core"}
                             :class "ns_core_Foo_root"}
                       props)
              "purple"))))))

(deftest test-reshape-props
  (are [props classes res] (= (oc/reshape-props props component-info classes) res)
    '(merge {:class "foo"}) #{"foo"} '(merge {:omcss$info {:ns-name "ns.core"
                                                      :component-name "Foo"}
                                         :class "ns_core_Foo_foo"})
    {:class "foo"} #{"foo"} {:omcss$info {:ns-name "ns.core"
                                     :component-name "Foo"}
                        :class "ns_core_Foo_foo"}
    {:class :foo} #{:foo} {:omcss$info {:ns-name "ns.core"
                                    :component-name "Foo"}
                       :class "ns_core_Foo_foo"}
    ;; TODO: is this intended behavior?
    ;; see OMCSS-17
    '(merge {:class (subs (str :foo) 1)}) #{:foo} '(merge
                                                 {:omcss$info {:component-name "Foo"
                                                               :ns-name "ns.core"},
                                                  :class (subs (str "ns_core_Foo_foo") 1)})))

(deftest test-format-class-names
  (are [cns res] (= (utils/format-class-names cns component-info) res)
    :foo "ns_core_Foo_foo"
    "foo" "ns_core_Foo_foo"
    [:foo] ["ns_core_Foo_foo"]
    ["foo"] ["ns_core_Foo_foo"]
    ["foo" :bar] ["ns_core_Foo_foo" "ns_core_Foo_bar"]
    '(keys {:root true}) '(keys {"ns_core_Foo_root" true})))

(deftest test-omcss-15
  (let [form '((let [color :red size :xl]
                 (dom/div {:class [color size]})))]
    (is (= (oc/reshape-render form component-info nil)
          '((let [color :red size :xl]
                 (dom/div {:class [color size]
                           :omcss$info {:component-name "Foo"
                                        :ns-name "ns.core"}})))))))

(deftest test-omcss-17
  (let [form '((dom/div nil
                 (inner {:class (flatten [:outer class])}
                   children)))]
    (is (= (oc/reshape-render form
             {:ns-name "om-css.devcards.bugs"
              :component-name "outer"}
             #{:outer})
          '((dom/div nil
              (inner {:omcss$info {:ns-name "om-css.devcards.bugs"
                                   :component-name "outer"}
                      :class (flatten ["om_css_devcards_bugs_outer_outer" class])}
                children)))))))

(deftest test-omcss-20
  (let [form '((let [dir "even"]
                 (dom/div
                   {:class (if (= dir "even") [:even] [])})))]
    (is (= (oc/reshape-render form component-info #{:even})
          '((let [dir "even"]
              (dom/div {:omcss$info {:component-name "Foo"
                                     :ns-name "ns.core"}
                        :class (if (= dir "even")
                                 ["ns_core_Foo_even"]
                                 [])})))))))

(deftest test-format-style-classes
  (let [{:keys [ns-name component-name]} component-info]
    (testing ""
      (are [style res] (= (oc/format-style-classes style ns-name component-name)
                         res)
        [:.root {:color :purple}] {:style [".ns_core_Foo_root"
                                           {:color :purple}]
                                   :classes #{:root}}
        [[:.root {:color :purple}]
         [:.section {:text-align :center}]] {:style [[".ns_core_Foo_root" {:color :purple}]
                                                     [".ns_core_Foo_section" {:text-align :center}]]
                                             :classes #{:root :section}}))
    (testing "OMCSS-19"
      (is (= (#'oc/format-garden-class-name ns-name component-name ["root"])
            ".ns_core_Foo_root"))
      (is (= (oc/format-style-classes
               [:h1.root {:color "#FFFFF"}]
               ns-name component-name)
            {:style ["h1.ns_core_Foo_root" {:color "#FFFFF"}]
             :classes #{:root}})))))

(deftest test-omcss-23
  (let [form '((outer
                 {:class :outer}
                 (inner {:class :inner} "inner")))]
    (is (= (oc/reshape-render form
             {:ns-name "om-css.devcards.bugs"
              :component-name "wrapper"}
             #{:outer :inner})
          '((outer {:omcss$info {:ns-name "om-css.devcards.bugs"
                                 :component-name "wrapper"}
                    :class "om_css_devcards_bugs_wrapper_outer"}
              (inner {:omcss$info {:ns-name "om-css.devcards.bugs"
                                   :component-name "wrapper"}
                      :class "om_css_devcards_bugs_wrapper_inner"} "inner")))))))

(deftest test-omcss-24
  (let [form '(((if true dom/div dom/span) children))]
    (is (= (oc/reshape-render form component-info #{})
          form))))

(deftest test-omcss-27
  (let [form '((dom/div (my-class :root)))]
    (is (= (oc/reshape-render form component-info #{:root})
          '((dom/div (my-class "ns_core_Foo_root")))))))

(deftest test-nested-fns-inside-element
  (let [form '((dom/div nil
                 "something"
                 (map-indexed
                   (fn [index _]
                     (dom/p {:class :hi} (str "index: " index)))
                   [1 2 3 4])))]
    (is (= ( oc/reshape-render form component-info #{:hi})
           '((dom/div nil
                 "something"
                 (map-indexed
                   (fn [index _]
                     (dom/p {:class "ns_core_Foo_hi"
                             :omcss$info {:component-name "Foo"
                                          :ns-name "ns.core"}}
                       (str "index: " index)))
                   [1 2 3 4]))))))
  (let [form '((dom/div nil
                 (->> [1 2] (map my-fn))))]
    (is (= (oc/reshape-render form component-info #{})
           form))))

(defui SimpleDefui
  oc/Style
  (style [_]
    [:.root {:color :green}])
  Object
  (render [this]
    (dom/div {:id "simple"
              :class :root}
      "root div")))

(defcomponent SimpleDefcomponent [props children]
  [:.inline {:display "inline"}]
  (dom/div {:class :inline} "inline div"))

(deftest test-om-css-cellophane
  (testing "cellophane & defui"
    (let [c ((cellophane/factory SimpleDefui))]
      (is (= (dom/render-to-str c)
            "<div><div id=\"simple\" class=\"om_css_tests_SimpleDefui_root\" data-reactid=\".0\">root div</div></div>"))))
  (testing "cellophane & defcomponent"
    (is (= (cdom/render-to-str (SimpleDefcomponent))
          "<div><div class=\"om_css_tests_SimpleDefcomponent_inline\" data-reactid=\".0\">inline div</div></div>"))))
