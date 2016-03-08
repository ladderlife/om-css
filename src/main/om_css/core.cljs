(ns om-css.core
  (:require-macros [om-css.core :refer [defui defcomponent]])
  (:require [clojure.string :as string]
            [om-css.utils :as utils]
            [om.next :as om]))

(defprotocol Style
  (style [this]))

(defn prefix-class-name
  [x class-name]
  "Given a component instance or a component class and a class-name,
   prefixes the class-name with the component info"
  (let [class (pr-str (cond-> x (om/component? x) type))
        [ns-name component-name] (string/split class #"/")
        info {:ns-name ns-name
              :component-name component-name}]
    (utils/format-class-name class-name info)))
