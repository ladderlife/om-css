(require '[figwheel-sidecar.repl :as r]
         '[figwheel-sidecar.repl-api :as ra])

(ra/start-figwheel!
  {:figwheel-options {}
   :build-ids ["devcards"]
   :all-builds
   [{:id "devcards"
     :figwheel {:devcards true}
     :source-paths ["src/main" "src/devcards"]
     :compiler {:main 'om-css.devcards.core
                :asset-path "/out"
                :output-to "resources/public/main.js"
                :output-dir "resources/public/out"
                :css-output-to "resources/public/main.css"
                :parallel-build true
                :compiler-stats true}}]})

(ra/cljs-repl)
