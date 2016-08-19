 (defproject com.ladderlife/om-css "0.6.0"
  :description "Colocated CSS in Om Next components"
  :url "http://github.com/ladderlife/om-css"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["clojars" {:sign-releases false}]]
  :dependencies [[org.clojure/clojure "1.9.0-alpha10" :scope "provided"]
                 [org.clojure/clojurescript "1.9.216" :scope "provided"]
                 [org.omcljs/om "1.0.0-alpha41" :scope "provided"]

                 [com.ladderlife/cellophane "0.3.5"]
                 [garden "1.3.2"]

                 [figwheel-sidecar "0.5.4-7" :scope "test"]
                 [devcards "0.2.1-7" :scope "test"]
                 [devcards-om-next "0.3.0" :scope "test"]]
  :profiles {:client-test {:dependencies [[cljsjs/react "15.3.0-0"]]
                           :plugins [[lein-doo "0.1.7"]
                                     [lein-cljsbuild "1.1.3"]]
                           :cljsbuild {:builds [{:id           "test"
                                                 :source-paths ["src/main" "src/test"]
                                                 :compiler     {:output-to "target/js/client_test.js"
                                                                :output-dir "target/js/out"
                                                                :main          om-css.runner
                                                                :target :nodejs
                                                                :optimizations :none}}]}}
             :dev {:dependencies [[sablono "0.7.4"]
                                  [com.cemerick/piggieback "0.2.1"
                                    :exclusions [org.clojure/clojurescript]]]}}
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :jar-exclusions [#"test" #"devcards" #"public" #"runner"]
  :source-paths ["src/main" "src/devcards" "src/test"]
  :test-paths ["src/test"]
  :clean-targets ^{:protect false} ["target"
                                    "resources/public/out"
                                    "resources/public/main.js"]
  :target-path "target")
