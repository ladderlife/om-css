(ns om-css.runner
  (:require [cljs.test :refer-macros [run-tests]]
            [cljs.nodejs]
            [om-css.tests]))

(enable-console-print!)

(defn main []
  (run-tests 'om-css.tests))

(set! *main-cli-fn* main)
