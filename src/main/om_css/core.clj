(ns om-css.core
  (:require [cljs.analyzer.api :as ana-api]
            [cljs.env :as env]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [garden.core :as garden]
            [om-css.dom :as dom]
            [om-css.utils :as utils])
  (:import (java.io FileNotFoundException)))

(def css (atom {}))

(defn reshape-props [props component-info classes-seen]
  (cond
    (map? props)
    (let [props' (->> props
                  (reduce
                    (fn [m [k v]]
                      (if (= k :class)
                        (assoc m k (utils/format-class-names
                                     v component-info classes-seen))
                        (assoc m k v)))
                    {:omcss$info component-info}))]
      props')

    (list? props)
    (let [[pre post] (split-with (complement map?) props)
          props' (concat (map #(cond-> %
                                 (keyword? %)
                                 (utils/format-class-names component-info #{:root}))
                           pre)
                   (map #(reshape-props % component-info classes-seen) post))]
      props')

    :else props))

(defn reshape-render
  [form component-info classes-seen]
  (loop [dt (seq form) ret []]
    (if dt
      (let [form (first dt)]
        (if (and (sequential? form) (not (empty? form)))
          (let [[[sym props :as pre] post] (split-at 2 form)
                coll-fn? (some #{(-> (str sym)
                                (string/split #"-")
                                first
                                symbol)}
                        ;; TODO: does this need to be hardcoded?
                        ['map 'keep 'run! 'reduce 'filter 'mapcat])
                props' (if (and coll-fn? (sequential? props))
                         (reshape-render props component-info classes-seen)
                         (reshape-props props component-info classes-seen))
                pre' (if (= (count pre) 2)
                       (list sym props')
                       (list sym))]
            (recur (next dt)
              (into ret
                [(cond->> (concat pre'
                            (reshape-render post component-info classes-seen))
                   (vector? form) (into []))])))
          (recur (next dt) (into ret [form]))))
      (seq ret))))

(defn reshape-defui [forms component-info classes-seen]
  (letfn [(split-on-object [forms]
            (split-with (complement '#{Object}) forms))
          (split-on-render [forms]
            (split-with
              (complement #('#{render} (first %)))
              forms))]
    (when (seq forms)
      (let [[pre [sym & obj-forms :as post]] (split-on-object forms)
            ret (into [] pre)]
        (if (seq post)
          (let [[pre [render & post]] (split-on-render obj-forms)]
            (into (conj ret sym)
              (concat pre [(reshape-render render component-info classes-seen)]
                post)))
          ret)))))

(defn get-style-form [forms]
  (loop [dt forms]
    (when (seq  dt)
      (let [form (first dt)]
        (if (and (not (sequential? form))
              (not (nil? form))
              (= (name form) "Style"))
          (fnext dt)
          (recur (rest dt)))))))

(defn reshape-style-form [form]
  (drop 2 form))

(defn get-component-style [forms]
  (-> forms
    get-style-form
    reshape-style-form
    first))

(defn- munge-ns-name [ns-name]
  (string/replace (munge ns-name) #"\." "_"))

(defn- format-garden-class-name [ns-name component-name cns]
  "generate namespace qualified classname"
  (reduce
    #(str %1 "." (munge-ns-name ns-name)
       "_" component-name "_" %2)
    "" cns))

(defn format-style-classes
  [styles ns-name component-name]
  (let [classes-seen (atom #{})]
    (letfn [(format-style-classes* [styles ns-name component-name]
              (->> styles
                (clojure.core/map
                  #(cond
                     (sequential? %)
                     (format-style-classes* % ns-name component-name)

                     (and (or (keyword? %) (string? %))
                       (.contains (name %) "."))
                     (let [cn (name %)
                           cns (remove empty? (string/split cn #"\."))
                           elem (when-not (.startsWith cn ".") (first cns))]
                       (swap! classes-seen into
                         (map keyword (cond-> cns
                                        (not (nil? elem)) rest)))
                       (str elem
                         (format-garden-class-name ns-name component-name
                           (if elem
                             (rest cns)
                             cns))))

                     (and (keyword? %) (.startsWith (name %) "$"))
                     (str "." (subs (name %) 1))

                     :else %))
                (into [])))]
      (let [styles (format-style-classes* styles ns-name component-name)]
        {:style styles
         :classes @classes-seen}))))

(defn infer-requires [{env-ns :ns :as env} forms]
  (letfn [(split-on-symbol [form]
            (split-with (complement symbol?) form))]
    (loop [dt (seq forms) ret []]
      (if dt
        (let [form (first dt)]
          (cond
            (sequential? form)
            (recur (next dt) (into ret (infer-requires env form)))

            (symbol? form)
            (let [ns (some-> (namespace form) symbol)
                  req (some->> ns
                        (get (:requires env-ns)))]
              (if req
                ;; look in requires
                (recur (next dt)
                  (conj ret `(~'require '[~req :as ~ns])))
                (if-not (nil? (re-find #"clojure.core/" (str (resolve form))))
                  ;; clojure function / var
                  (recur (next dt) ret)
                  (do
                    (let [sym-ns (some-> env-ns :defs form :name namespace symbol)
                          sym-ns (when sym-ns
                                   `(~'use '~sym-ns))
                          use-ns (when-let [kv (find (:uses env-ns) form)]
                                   `(~'use '[~(second kv) :only [~(first kv)]]))]
                      (if (or (= sym-ns (-> env-ns :name)) use-ns)
                        (recur (next dt)
                          (conj ret (when sym-ns sym-ns) (when use-ns use-ns)))
                        (recur (next dt) ret)))))))
            :else (recur (next dt) ret)))
        ret))))

(defn eval-component-style [style env]
  (let [ns-name (-> env :ns :name str)
        requires (cons '(clojure.core/refer 'clojure.core)
                   (infer-requires env style))]
    (try
      (some->> style
        list
        (concat requires)
        (cons 'do)
        eval)
      (catch FileNotFoundException e
        (throw (IllegalArgumentException.
                 "Constants must be in a .cljc file."))))))

(defn defui* [name forms env]
  (let [ns-name (-> env :ns :name str)
        component-name (str name)
        component-style (-> forms
                          get-component-style
                          (eval-component-style env))
        {:keys [style classes]} (when component-style
                                  (format-style-classes component-style
                                    ns-name component-name))
        css-str (when style
                  (garden/css style))
        component-info {:ns-name ns-name
                        :component-name (str name)
                        :classes classes}
        forms (reshape-defui forms component-info classes)
        name (cond-> name
               (-> name meta :once) (vary-meta assoc :once true))]
    (when css-str
      (swap! css assoc [ns-name name] css-str))
    `(om.next/defui ~name ~@forms)))

(defmacro defui [name & forms]
  (defui* name forms &env))

(defn defcomponent*
  [env name [props children :as args] component-style body]
  "Example usage:
   (defcomponent foo
     [props children]
     ;; optional styles vector
     [[:.foo {:color :green}]
     (dom/div {:class :foo}
              children))
   (foo (dom/a {:href \"http://google.com\"}))
   "
  (when-not (and (vector? args) (= (count args) 2)
              ;; arguments are vectors or destructuring maps
              (or (symbol? (first args)) (map? (first args)))
              (or (symbol? (second args)) (map? (second args))))
    (throw (IllegalArgumentException.
             (str "Malformed `defcomponent`. Correct syntax: "
               "`(defcomponent [props children] "
               "[:.optional {:styles :vector}]"
               "(dom/element {:some :props} :children))`"))))
  (let [ns-name (-> env :ns :name str)
        component-name (str name)
        component-style' (some-> component-style
                           (eval-component-style env))
        {:keys [style classes]} (when component-style'
                                  (format-style-classes component-style'
                                    ns-name component-name))
        css-str (some-> style
                  garden/css)
        component-info {:ns-name ns-name
                        :component-name component-name
                        :classes classes}
        body (if (vector? (first body))
               (map #(into [] (reshape-render % component-info #{})) body)
               (reshape-render body component-info classes))]
    (when css-str
      (swap! css assoc [ns-name name] css-str))
    `(defn ~name [& params#]
       (let [[props# children#] (om-css.dom/parse-params params#)
             ~props (assoc props# :omcss$info ~component-info)
             ~children children#]
         ~@body))))

(defmacro defcomponent
  [name props&children & [style & rest :as body]]
  (defcomponent* &env name props&children
    (when (vector? style)
      style)
    (if (vector? style)
      rest
      body)))

;; TODO: can we make this not open the file for each atom state change?
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
                    (butlast)
                    vec
                    (conj default-fname))))]
    (add-watch css :watcher
      (fn [k atom old-state new-state]
        (with-open [out ^java.io.Writer (io/make-writer fname {})]
          (binding [*out* out]
            (println (string/join "\n" (vals new-state)))
            (println)))))))

(setup-io!)


