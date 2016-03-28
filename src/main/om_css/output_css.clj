(ns om-css.output-css
  (:require [cljs.env :as env]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn setup-io! []
     (let [opts (some-> env/*compiler*
                  deref
                  :options)
           default-fname "out.css"
           fname (or (:css-output-to opts)
                   (str (:output-dir opts) default-fname)
                   (string/join "/"
                     (-> (:output-to opts)
                       (string/split #"/")
                       butlast
                       vec
                       (conj default-fname))))]
       (add-watch om-css.core/css :watcher
         (fn [k atom old-state new-state]
           (with-open [out ^java.io.Writer (io/make-writer fname {})]
             (binding [*out* out]
               (println (string/join "\n" (vals new-state)))
               (println)))))))

(setup-io!)
