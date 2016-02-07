(ns om-css.dom
  (:refer-clojure :exclude [map meta time])
  (:require [clojure.string :as string]
            [garden.core :as garden]
            [om.dom :as dom]))

;;; defcomponent

(defmacro defcomponent
  [component-name [props children] & body]
  "Example usage:
  (defcomponent foo
    [props children]
    (dom/div {:class \"foo\"}
             children))

  (foo (dom/a {:href \"http://google.com\"}))
  "
  (list 'defn component-name '[& params]
        (concat (list 'let [[props (or children '_)] `(parse-params ~'params)])
                body)))

;;; generate all form tags

(def form-tags '[input textarea option])

(def all-tags (concat dom/tags form-tags))

(defmacro gen-tag-fns
  []
  `(do
     ~@(clojure.core/map
         (fn [tag]
           `(defn ~tag [~'this & ~'params]
              (apply render-elem ~(symbol "om.dom" (name tag)) ~'this ~'params)))
         all-tags)))

(defn reshape-render
  ([form]
   (reshape-render form nil))
  ([form this-arg]
   (loop [dt (seq form) ret [] this-arg this-arg]
     (if dt
       (let [form (first dt)]
         (if (and (sequential? form) (not (empty? form)))
           (let [first-form (name (first form))]
             (if (some #{(symbol  first-form)} all-tags)
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

;; TODO: add ns format to the styles
(defn get-component-style [forms]
  (-> forms
    get-style-form
    reshape-style-form
    first))

(defn- format-class-name [ns-name class-name]
  "generate namespace qualified classname"
  (let [ns-name ns-name
        class-name (name class-name)
        res (str "."
              (string/replace (munge ns-name) #"\." "_")
              "_"
              (subs class-name 1))]
    res))

;; TODO: styles is last arg because of thread-last in `defui*`
(defn format-style-classes [ns-name styles]
  (->> styles
    (clojure.core/map
      #(cond
         (sequential? %) (format-style-classes ns-name %)
         (and (keyword? %) (.startsWith (name %) ".")) (format-class-name ns-name %)
         :else %))
    (into [])))

;; TODO: runtime evaluation to support vars and fns
;; currently `load-string` fails if the style contains a var/function
;; call outside the scope of clojure.core
(defn defui* [name forms env]
  (let [ns-name (-> env :ns :name str)
        component-style (->> (get-component-style forms)
                          (str "(clojure.core/refer 'clojure.core)")
                          load-string
                          (format-style-classes ns-name))
        css-str (garden/css component-style)
        forms (reshape-defui forms)
        forms (concat forms (list 'static 'field 'ns ns-name))]
    `(om.next/defui ~name ~@forms)))

(defmacro defui [name & forms]
  (defui* name forms &env))

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

