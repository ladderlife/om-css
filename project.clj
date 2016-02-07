(defproject om-css "0.1.0-SNAPSHOT"
  :description "Om Next + CSS"
  :url "http://github.com/ladderlife/om-css"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.omcljs/om "1.0.0-alpha31-SNAPSHOT"]
                 [garden "1.3.0"]

                 [figwheel-sidecar "0.5.0-4" :scope "test"]
                 [devcards "0.2.1-6" :scope "test"]
                 [devcards-om-next "0.1.1" :scope "test"]]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :source-paths ["src/main" "src/devcards" "src/test"]
  :clean-targets ^{:protect false} [["resources/public/out"
                                     "resources/public/main.js"]]
  :target-path "target")
