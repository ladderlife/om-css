(ns om-css.tests
  (:require [cljs.test :refer-macros [deftest testing is are run-tests]]
            [cljsjs.react]
            [om-css.core :as oc :refer-macros [defui]]
            [om-css.utils :as utils]))

(def component-info
  {:component-name "Foo"
   :ns-name "ns.core"})

(deftest test-format-class-name
  (are [cn res] (= (utils/format-class-name cn component-info) res)
    :foo "ns_core_Foo_foo"
    "foo" "ns_core_Foo_foo")
  (is (= (utils/format-class-name
           :foo
           {:ns-name "ns.core"
            :component-name "ns.core/Foo"})
        "ns_core_Foo_foo")))

(deftest test-format-dom-class-names
  (are [cns classes-seen res] (= (utils/format-dom-class-names cns component-info classes-seen) res)
    "ns_core_Foo_foo" #{} "ns_core_Foo_foo"
    :foo #{:foo} "ns_core_Foo_foo"
    [:foo] #{:foo} "ns_core_Foo_foo"
    [:foo :bar] #{:foo :bar} "ns_core_Foo_foo ns_core_Foo_bar"
    ["ns_core_Foo_foo"] nil "ns_core_Foo_foo"
    ["ns_core_Foo_foo"] nil "ns_core_Foo_foo"
    ["ns_core_Foo_foo" "ns_core_Foo_bar"] nil "ns_core_Foo_foo ns_core_Foo_bar"
    ["ns_core_Foo_foo" :bar] #{:bar} "ns_core_Foo_foo ns_core_Foo_bar"))

(defui StyledComponent
  static oc/Style
  (style [this]
    [[:.some-class {:text-align "center"}]]))

(deftest test-prefix-class-name
  (is (= (oc/prefix-class-name StyledComponent :some-class)
         "om_css_tests_StyledComponent_some-class")))
