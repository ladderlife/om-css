(ns om-css.core
  (:require [om-css.dom :as dom]
            [cljs.analyzer.api :as ana-api]
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

(defn reshape-post-elem [form this-arg]
  (if (map? (first form))
    (let [props (->> (first form)
                  (map (fn [[k v :as attr]]
                         (if (= k :class)
                           [k (format-class-names this-arg v)]
                           attr)))
                  (into {}))
          props' (assoc props :omcss$this this-arg)]
      (cons props' (rest form)))
    form))

;; TODO: this function can be cleaned up since we're no longer attaching `this-arg`
(defn reshape-render
  [form this-arg]
  (loop [dt (seq form) ret []]
    (if dt
      (let [form (first dt)]
        (if (and (sequential? form) (not (empty? form)))
          (let [first-form (name (or (first form) ""))
                tag? (some #{(symbol first-form)} dom/all-tags)
                bind? (some #{(-> (str first-form)
                                (string/split #"-")
                                first
                                symbol)}
                        ;; TODO: does this need to be hardcoded?
                        ['let 'binding 'when 'if])]
            (let [[pre post] (split-at (cond-> 1 bind? inc) form)
                  post' (reshape-post-elem post this-arg)]
              (if (or tag? bind?)
                (recur (next dt)
                  (conj ret
                    (concat pre (reshape-render post' this-arg))))
                (recur (next dt) (into ret [(concat pre post')])))))
          (recur (next dt) (into ret [form]))))
      (seq ret))))

(defn reshape-defui [forms this-arg]
  (letfn [(split-on-object [forms]
            (split-with (complement '#{Object}) forms))
          (split-on-render [forms]
            (split-with
              (complement #('#{render} (first %)))
              forms))]
    (loop [dt (seq forms) dt' []]
      (if dt
        (let [[pre [sym & obj-forms :as post]] (split-on-object dt)
              dt' (into dt' pre)]
          (if (seq post)
            (let [[pre [render & post]] (split-on-render obj-forms)
                  render' (reshape-render render this-arg)]
              (recur nil (-> (conj dt' sym)
                           (into (concat pre [render'] post)))))
            (recur nil dt')))
        dt'))))

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
             ~props props#
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
  (let [opts (ana-api/get-options)
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


(comment
  (reshape-defui
    '(om/IQuery
        (query [this])
      om/Ident
      (ident [this])
      Object
      (componentWillMount [this])
      (render [dia]
        (dom/div nil (dom/div nil "3")))
      static field a 3
      static om/IQuery
      (query [this] [:a])))

  (get-component-style
    '(static om/IQuery
      (query [this])
      static oc/Style
      (style [_]
        [:root {:color "#FFFFF"}
         :section (merge {} ;;css/default-section
                    {:background-color :green})])
     static om/Ident
     (ident [this])
     Object
     (render [this])
     static om/IQueryParams
     (params [this])))

  (get-style-form
    '(Object
      (render [this])
      static om/Ident
      (ident [this])))

  (reshape-style-form
    '(style [_]
       [:root {:color "#FFFFF"}
        :section (merge {} {:background-color :green})]))

  (reshape-render
    '((let [x true]
        (dom/div {:class [:root :active]}
          "div with class root")))
    {:component-name "FooComp"
     :ns-name "some-ns.core"})

  (reshape-render
    '((dom/div {:id "nested-defcomponent"
                :class :nested-defcomponent}
        "Nested `defcomponent` example"
        (defcomponent-example {:class :some} "some text")))
    {:component-name "FooComp"
     :ns-name "some-ns.core"})

  (reshape-render
    '((omcss-7-component-1 {:class [:root]}))
    {:component-name "FooComp"
     :ns-name "some-ns.core"})
  )
