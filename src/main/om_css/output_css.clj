(ns om-css.output-css
  (:require [cljs.analyzer.api :as ana-api]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn setup-io! []
  (let [{:keys [css-output-to output-dir output-to]} (ana-api/get-options)
        default-fname "out.css"
        fname (or css-output-to
                (str output-dir default-fname)
                (string/join "/"
                  (-> output-to
                    (string/split  #"/")
                    pop
                    (conj default-fname))))]
    (add-watch om-css.core/css :watcher
      (fn [k atom old-state new-state]
        (with-open [out ^java.io.Writer (io/make-writer fname {})]
          (binding [*out* out]
            (println (string/join "\n" (vals new-state)))
            (println)))))))

(setup-io!)
