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

(defn format-class-names [component-info cns]
  (if-not (or (vector? cns)
            (string? cns)
            (keyword? cns))
    cns
    (let [cns' (map #(utils/format-class-name component-info %)
                 (if (sequential? cns) cns [cns]))]
      (if (sequential? cns)
        (into [] cns')
        (first cns')))))

(defn reshape-props [props component-info]
  (cond
    (map? props)
    (let [props' (->> props
                  (map (fn [[k v :as attr]]
                         (if (= k :class)
                           [k (format-class-names component-info v)]
                           attr)))
                  (into {:omcss$info component-info}))]
      props')

    (list? props)
    (let [[pre post] (split-with (complement map?) props)
          props' (concat pre (map #(reshape-props % component-info) post))]
      props')

    :else props))

(defn reshape-render
  [form component-info]
  (loop [dt (seq form) ret []]
    (if dt
      (let [form (first dt)]
        (if (and (sequential? form) (not (empty? form)))
          (let [first-form (name (first form))
                tag? (some #{(symbol first-form)} dom/all-tags)
                bind? (some #{(-> (str first-form)
                                (string/split #"-")
                                first
                                symbol)}
                        ;; TODO: does this need to be hardcoded?
                        ['let 'binding 'when 'if])
                [[sym props :as pre] post] (split-at 2 form)
                props' (reshape-props props component-info)
                pre' (if (= (count pre) 2)
                       (list sym props')
                       (list sym))]
            (recur (next dt)
              (into ret
                [(concat pre'
                   (cond-> post
                     (or tag? bind?) (reshape-render component-info)))])))
          (recur (next dt) (into ret [form]))))
      ret)))

(defn reshape-defui [forms component-info]
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
              (concat pre [(reshape-render render component-info)]
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

(defn- format-garden-class-name [ns-name component-name class-name]
  "generate namespace qualified classname"
  (let [ns-name ns-name
        class-name (name class-name)]
    (str "." (munge-ns-name ns-name)
      "_" component-name "_" (subs class-name 1))))

(defn format-style-classes [styles ns-name component-name]
  (->> styles
    (clojure.core/map
      #(cond
         (sequential? %)
         (format-style-classes % ns-name component-name)

         (and (keyword? %) (.startsWith (name %) "."))
         (format-garden-class-name ns-name component-name %)

         (and (keyword? %) (.startsWith (name %) "$"))
         (str "." (subs (name %) 1))

         :else %))
    (into [])))

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
                    (let [sym-ns (some-> env-ns :defs form :name namespace symbol)]
                      (if (= sym-ns (-> env-ns :name))
                        (recur (next dt) (conj ret `(~'use '~sym-ns)))
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
                 "Constants must be in a .cljs file."))))))

(defn defui* [name forms env]
  (let [ns-name (-> env :ns :name str)
        component-name (str name)
        component-style (-> forms
                          get-component-style
                          (eval-component-style env)
                          (format-style-classes ns-name component-name))
        css-str (when component-style
                  (garden/css component-style))
        component-info {:ns-name ns-name
                        :component-name (str name)}
        forms (reshape-defui forms component-info)
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
  (when-not (and (vector? args) (= (count args) 2))
    (throw (IllegalArgumentException.
             (str "Malformed `defcomponent`. Correct syntax: "
               "`(defcomponent [props children] "
               "[:.optional {:styles :vector}]"
               "(dom/element {:some :props} :children))`"))))
  (let [ns-name (-> env :ns :name str)
        component-name (str name)
        component-style' (some-> component-style
                           (eval-component-style env)
                           (format-style-classes ns-name component-name))
        css-str (some-> component-style'
                  garden/css)
        component-info {:ns-name ns-name
                        :component-name component-name}
        body (reshape-render body component-info)]
    (when css-str
      (swap! css assoc [ns-name name] css-str))
    `(defn ~name [& params#]
       (let [[props# children#] (om-css.dom/parse-params params#)
             ~props (dissoc  props# :omcss$info)
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


