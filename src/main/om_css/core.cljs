(ns om-css.core
  (:require-macros [om-css.core :refer [defui]])
  (:require [om.next :as om]))

(defprotocol Style
  (style [this]))


