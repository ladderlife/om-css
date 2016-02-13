(require '[cljs.build.api :as b])

(b/build (b/inputs "src/main" "src/test")
  {:target :nodejs
   :main 'om-css.runner
   :output-to "target/test/test.js"
   :output-dir "target/test/out"
   :parallel-build true
   :compiler-stats true
   :static-fns true
   :optimizations :none})


(System/exit 0)
