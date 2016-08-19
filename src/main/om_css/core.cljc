(ns om-css.core
  #?(:cljs (:require-macros [om-css.core :refer [defui defcomponent]]
                            [om-css.output-css]))
  (:require #?@(:clj  [[om-css.dom :as dom]
                       [garden.core :as garden]
                       [cljs.analyzer.api :as ana]]
                :cljs [[om.next :as om]])
            [clojure.string :as string]
            [om-css.utils :as utils #?@(:clj [:refer [if-cljs]])])
  #?(:clj (:import (java.io FileNotFoundException))))

(defprotocol Style
   (style [this]))

#?(:cljs
   (defn prefix-class-name
     [x class-name]
     "Given a component instance or a component class and a class-name,
      prefixes the class-name with the component info"
     (let [class (pr-str (cond-> x (om/component? x) type))
           [ns-name component-name] (string/split class #"/")
           info {:ns-name ns-name
                 :component-name component-name}]
       (utils/format-class-name class-name info))))

#?(:clj
   (def css (atom {})))

#?(:clj
   (defn reshape-props [props component-info classes-seen]
     (cond
       (map? props)
       (let [props' (->> props
                      (reduce
                        (fn [m [k v]]
                          (if (= k :class)
                            (assoc m k (utils/format-unevaluated-class-names
                                         v component-info classes-seen))
                            (assoc m k v)))
                        {:omcss$info component-info}))]
         props')

       (list? props)
       (let [[pre post] (split-with (complement map?) props)
             props' (concat (map #(cond-> %
                                    (keyword? %)
                                    (utils/format-unevaluated-class-names component-info classes-seen))
                              pre)
                      (map #(reshape-props % component-info classes-seen) post))]
         props')

       :else props)))

#?(:clj
   (defn reshape-render
     ([form component-info classes-seen]
      (reshape-render nil form component-info classes-seen))
     ([env form component-info classes-seen]
      (loop [dt (seq form) ret []]
        (if dt
          (let [form (first dt)]
            (if (and (sequential? form) (not (empty? form)))
              (let [[[sym props :as pre] post] (split-at 2 form)
                    sablono? (when (and env (symbol? sym))
                               (= (-> (ana/resolve env sym) :name)
                                  'sablono.core/html))
                    coll-fn? (some #{(-> (str sym)
                                       (string/split #"-")
                                       first
                                       symbol)}
                               ;; TODO: does this need to be hardcoded?
                               ['map 'keep 'run! 'reduce 'filter 'mapcat])
                    props' (if (and coll-fn? (sequential? props))
                             (reshape-render env props component-info classes-seen)
                             (reshape-props props component-info classes-seen))
                    props-omitted? (and (sequential? props)
                                     (let [tag (first props)]
                                       (and (or sablono? (symbol? tag) (keyword? tag))
                                            (some #{(symbol (name tag))} dom/all-tags))))
                    pre' (if (and (= (count pre) 2)
                               (not props-omitted?))
                           (list sym props')
                           (list sym))
                    post (cond->> post
                           props-omitted? (cons props'))]
                (recur (next dt)
                  (into ret
                    [(cond->> (concat pre'
                                (reshape-render env post component-info classes-seen))
                       (vector? form) (into []))])))
              (recur (next dt) (into ret [form]))))
          (seq ret))))))

#?(:clj
   (defn reshape-defui [env forms component-info classes-seen]
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
                 (concat pre [(reshape-render env render component-info classes-seen)]
                   post)))
             ret))))))

#?(:clj
   (defn get-style-form [forms]
     (loop [dt forms]
       (when (seq  dt)
         (let [form (first dt)]
           (if (and (not (sequential? form))
                 (not (nil? form))
                 (= (name form) "Style"))
             (fnext dt)
             (recur (rest dt))))))))

#?(:clj
   (defn reshape-style-form [form]
     (drop 2 form)))

#?(:clj
   (defn get-component-style [forms]
     (-> forms
       get-style-form
       reshape-style-form
       first)))

#?(:clj
   (defn- munge-ns-name [ns-name]
     (string/replace (munge ns-name) #"\." "_")))

#?(:clj
   (defn- format-garden-class-name [ns-name component-name cns]
     "generate namespace qualified classname"
     (reduce
       #(str %1 "." (munge-ns-name ns-name)
          "_" component-name "_" %2)
       "" cns)))

#?(:clj
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
            :classes @classes-seen})))))

#?(:clj
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
           ret)))))

#?(:clj
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
                    "Constants must be in a .cljc file.")))))))

#?(:clj
   (defn- get-ns-name [env]
     (if-let [ns (:ns env)]
       (str (:name ns))
       (str (ns-name *ns*)))))

#?(:clj
   (defn defui* [name forms env]
     (let [ns-name (get-ns-name env)
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
           forms (reshape-defui env forms component-info classes)
           name (cond-> name
                  (-> name meta :once) (vary-meta assoc :once true))]
       (when css-str
         (swap! css assoc [ns-name name] css-str))
       `(if-cljs
          (om.next/defui ~name ~@forms)
          (cellophane.next/defui ~name ~@forms)))))

#?(:clj
   (defmacro defui [name & forms]
     (defui* name forms &env)))

#?(:clj
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
     (let [ns-name (get-ns-name env)
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
                  (map #(into [] (reshape-render env % component-info #{})) body)
                  (reshape-render env body component-info classes))]
       (when css-str
         (swap! css assoc [ns-name name] css-str))
       `(defn ~name [& params#]
          (let [[props# children#] (om-css.dom/parse-params params#)
                ~props (assoc props# :omcss$info ~component-info)
                ~children children#]
            ~@body)))))

#?(:clj
   (defmacro defcomponent
     [name props&children & [style & rest :as body]]
     (defcomponent* &env name props&children
       (when (vector? style)
         style)
       (if (vector? style)
         rest
         body))))
