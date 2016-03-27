(ns om-css.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [om-css.tests]))

(doo-tests 'om-css.tests)
