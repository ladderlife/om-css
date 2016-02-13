set -e

lein trampoline run -m clojure.main scripts/test_cljs.clj
node target/test/test.js
