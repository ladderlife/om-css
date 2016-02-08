(ns om-css.core
  (:require [om-css.dom :as dom]
            [cljs.analyzer.api :as ana-api]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [garden.core :as garden]))

(def css (atom {}))

(defn reshape-render
  ([form]
   (reshape-render form nil))
  ([form this-arg]
   (loop [dt (seq form) ret [] this-arg this-arg]
     (if dt
       (let [form (first dt)]
         (if (and (sequential? form) (not (empty? form)))
           (let [first-form (name (first form))]
             (if (some #{(symbol  first-form)} dom/all-tags)
               (let [[pre post] (split-at 1 form)]
                 (recur (next dt)
                   (conj ret
                     (concat pre (list this-arg) (reshape-render post this-arg)))
                   this-arg))
               (recur (next dt) (into ret [form])
                 (if (vector? form)
                   (first form)
                   this-arg))))
           (recur (next dt) (into ret [form]) this-arg)))
       (seq ret)))))

(defn reshape-defui [forms]
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
                  new-render (reshape-render render)]
              (recur nil (-> (conj dt' sym)
                           (into (concat pre [new-render] post)))))
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

(defn- format-class-name [ns-name component-name class-name]
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

         (and (keyword? %)
           (.startsWith (name %) "."))
         (format-class-name ns-name component-name %)

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
        forms (reshape-defui forms)
        forms (concat forms (list 'static 'field 'ns ns-name))]
    (when css-str
      (swap! css assoc [ns-name name] css-str))
    `(om.next/defui ~name ~@forms)))

(defmacro defui [name & forms]
  (defui* name forms &env))

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
  )
