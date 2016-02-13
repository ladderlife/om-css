(ns om-css.tests
  (:require [cljs.test :refer-macros [deftest testing is are run-tests]]
            [om-css.utils :as utils]))

(def component-info
  {:component-name "Foo"
   :ns-name "ns.core"})

(deftest test-format-class-name
  (are [cn res] (= (utils/format-class-name component-info cn) res)
    :foo "ns_core_Foo_foo"
    "foo" "ns_core_Foo_foo")
  (is (= (utils/format-class-name
           {:ns-name "ns.core"
            :component-name "ns.core/Foo"}
           :foo)
        "ns_core_Foo_foo")))

(deftest test-format-class-names
  (are [cns res] (= (utils/format-class-names component-info cns) res)
    "ns_core_Foo_foo" "ns_core_Foo_foo"
    :foo "ns_core_Foo_foo"
    [:foo] "ns_core_Foo_foo"
    [:foo :bar] "ns_core_Foo_foo ns_core_Foo_bar"
    ["ns_core_Foo_foo"] "ns_core_Foo_foo"
    ["ns_core_Foo_foo"] "ns_core_Foo_foo"
    ["ns_core_Foo_foo" "ns_core_Foo_bar"] "ns_core_Foo_foo ns_core_Foo_bar"
    ["ns_core_Foo_foo" :bar] "ns_core_Foo_foo ns_core_Foo_bar"))
