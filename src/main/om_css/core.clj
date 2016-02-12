(ns om-css.core
  (:require [om-css.dom :as dom]
            [cljs.analyzer.api :as ana-api]
            [cljs.env :as env]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [garden.core :as garden]))

(def css (atom {}))

(defn- format-class-name [this-arg class-name]
  "generate namespace qualified classname"
  (let [ns-name (:ns-name this-arg)
        class-name (name class-name)
        component-name (-> (:component-name this-arg)
                         (string/split #"/")
                         last)]
    (str (string/replace (munge ns-name) #"\." "_")
      "_" component-name "_" class-name)))

(defn format-class-names [this-arg cns]
  (if-not (or (vector? cns)
            (string? cns)
            (keyword? cns))
    cns
    (let [cns' (map #(format-class-name this-arg %)
                 (if (sequential? cns) cns [cns]))]
      (if (sequential? cns)
        (into [] cns')
        (first cns')))))

(defn reshape-props [props this-arg]
  (cond
    (map? props)
    (let [props' (->> props
                  (map (fn [[k v :as attr]]
                         (if (= k :class)
                           [k (format-class-names this-arg v)]
                           attr)))
                  (into {:omcss$this this-arg}))]
      props')

    (list? props)
    (let [[pre post] (split-with (complement map?) props)
          props' (concat pre (doall (map #(reshape-props % this-arg) post)))]
      props')

    :else props))

(defn reshape-render
  [form this-arg]
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
                props' (reshape-props props this-arg)
                pre' (if (= (count pre) 2)
                       (list sym props')
                       (list sym))]
            (recur (next dt)
              (into ret
                [(concat pre'
                   (cond-> post
                     (or tag? bind?) (reshape-render this-arg)))])))
          (recur (next dt) (into ret [form]))))
      ret)))

(defn reshape-defui [forms this-arg]
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
              (concat pre [(reshape-render render this-arg)]
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

;; styles is the last arg because of thread-last in `defui*`
(defn format-style-classes [ns-name component-name styles]
  (->> styles
    (clojure.core/map
      #(cond
         (sequential? %)
         (format-style-classes ns-name component-name %)

         (and (keyword? %) (.startsWith (name %) "."))
         (format-garden-class-name ns-name component-name %)

         (and (keyword? %) (.startsWith (name %) "$"))
         (str "." (subs (name %) 1))

         :else %))
    (into [])))

;; TODO: runtime evaluation to support vars and fns
;; currently `load-string` fails if the style contains a var/function
;; call outside the scope of clojure.core
(defn defui* [name forms env]
  (let [ns-name (-> env :ns :name str)
        component-style (some->> (get-component-style forms)
                          (str "(clojure.core/refer 'clojure.core)")
                          load-string
                          (format-style-classes ns-name (str name)))
        css-str (when component-style
                  (garden/css component-style))
        this-arg {:ns-name ns-name
                  :component-name (str name)}
        forms (reshape-defui forms this-arg)]
    (when css-str
      (swap! css assoc [ns-name name] css-str))
    `(om.next/defui ~name ~@forms)))

(defmacro defui [name & forms]
  (defui* name forms &env))

(defn defcomponent*
  [env component-name [props children :as args] component-style body]
  "Example usage:
   (defcomponent foo
     [props children]
     ;; optional styles vector
     [[:.foo {:color :green}]
     (dom/div {:class :foo}
              children))
   (foo (dom/a {:href \"http://google.com\"}))
   "
  (let [ns-name (-> env :ns :name str)
        css-str (when component-style
                  (garden/css (format-style-classes ns-name
                                (str component-name)
                                component-style)))
        _ (when css-str
            (swap! css assoc [ns-name component-name] css-str))
        this-arg {:ns-name ns-name
                  :component-name (str component-name)}
        body (reshape-render body this-arg)]
    (when-not (and (vector? args) (= (count args) 2))
      (throw (IllegalArgumentException.
               (str "Malformed `defcomponent`. Correct syntax: "
                 "`(defcomponent [props children] "
                 "[:.optional {:styles :vector}]"
                 "(dom/element {:some :props} :children))`"))))
    `(defn ~component-name [& params#]
       (let [[props# children#] (om-css.dom/parse-params params#)
             ~props (dissoc  props# :omcss$this)
             ~children children#]
         ~@body))))

(defmacro defcomponent
  [component-name props&children & [style & rest :as body]]
  (defcomponent* &env component-name props&children
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


