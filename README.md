# Om-css [![Circle CI](https://circleci.com/gh/ladderlife/om-css.svg?style=svg)](https://circleci.com/gh/ladderlife/om-css)

Colocated CSS in Om Next components.

## Contents

- [Installation](#installation)
- [Guide](#guide)
  - [Getting started](#getting-started)
    - [Usage with boot](#usage-with-boot)
  - [Defining components](#defining-components)
    - [`defui`](#defui)
    - [`defcomponent`](#defcomponent)
  - [Referring to global classes](#referring-to-global-classes)
  - [User-defined variables in colocated styles](#user-defined-variables-in-colocated-styles)
- [Copyright & License](#copyright--license)


## Installation

Add Om-css to your dependencies:

[![Clojars Project](https://clojars.org/com.ladderlife/om-css/latest-version.svg)](https://clojars.org/com.ladderlife/om-css)

## Guide

### Getting started

To get started, `:require` Om-css in your project:

```clojure
(ns my-ns.core
  (:require [om-css.core :as oc :refer-macros [defui]]
            [om-css.dom :as dom])
```

**Note**: In order to use the colocated style capabilities provided by Om-css, using its own `defui` and `om-css.dom` is required over Om's.

Om-css provides a way to collocate CSS styles in components. However, this alone is not enough to get actual stylesheet files that you can link to in your web pages. Hence, Om-css will generate such css file for you. By default, the generated file will be called `out.css` and will be output at the root of your project. You can, however, instruct Om-css to output to a particular file. Simply add a `:css-output-to` option to the ClojureScript compiler options. Below is an example. [Here](./scripts/figwheel.clj#L15)'s a real example.

```clojure
:compiler {:main 'om-css-example.core
           :asset-path "out"
           :output-to "resources/public/main.js"
           :output-dir "resources/public/out"
           :source-map true
           :optimizations :none
           :css-output-to "resources/public/main.css"}
```

#### Usage with boot

The `:css-output-to` option of om-css is a bit of a mismatch with boot, because boot will write to an new folder after every cljs compile. The solution here is to not set the `:css-output-to` option. Then om-css will write the css file in the same place as your cljs-output. So let's say you have a `public/js/main.cljs.edn` file then boot-cljs will write to `public/js/main.js` and om-css will write to `public/js/main.outout.css`. If you would like a nicer css filename you can use the `sift` task of boot for the preceding case you could use it like this:

```clojure
(deftask compile-cljs-and-css
  (comp
   (cljs :ids #{"public/js/main"})
   (sift :move {#"^public\/js\/main\.outout\.css$" "public/css/next.css"})))
```

### Defining components

#### `defui`

Components are defined as in Om Next. In addition, Om-css provides the `Style` protocol, which you must implement in order to get all the functionality Om-css provides. This protocol consists of a single function, `style`, which must return a [Garden](https://github.com/noprompt/garden) styles vector.

In the example shown below, we implement a simple component that declares a style consisting of a single class, `:root`. In the component's `render` function, the props passed to React elements need not be JavaScript objects, and may instead be regular Clojure(Script) maps. The `:class` prop is special to Om-css in the sense that it will be prefixed with the namespace and component name so that there are no clashes between components that declare classes with the same names. In our simple example, `:root` will be transformed to `"my_ns_core_SimpleComponent_root"`, and Om-css will generate CSS with the same class name.

```clojure
(ns my-ns.core)

(defui SimpleComponent
  oc/Style
  (style [_]
    [[:.root {:color :yellow}]])
  Object
  (render [this]
    (dom/div {:class :root} ;; <= use a vector `[:one :two]` to add multiple classes to an element
      "Div with class :root"))
```

#### `defcomponent`

`defcomponent` is syntactic sugar for simple React elements. These can optionally include a Garden styles vector in their implementation, before the element implementation itself. The following example demonstrates a `defcomponent` implementation.

```clojure
(defcomponent element-with-style [props children]
  [[:.example-class {:background-color "tomato"}]] ;; <= optional
  (dom/div {:class :example-class}
    "Nested `defcomponent` example"))
```

### Referring to global classes

Collocating CSS within components is not enough for every use case. At times, you may want to use a global CSS class that is defined somewhere else. Referring to classes defined in another location is possible both in Om-css's styles vector and in the components implementation.

To reference externally defined CSS classes in the colocated styles, simply use the `$` prefix instead of the normal CSS `.` prefix. To do so in the components `render` implementation, use either one of `:className` or `:class-name`. Om-css will only prefix classes that appear in the `:class` prop.

The following example shows how this is done in practice. The CSS generated by the styles vector of `example-component` is what you might expect and is presented below the component implementation.

```clojure
(ns my-ns.core)

(defcomponent example-component [props children]
  [:$desktop
   [:.root {:background-color "tomato"}]]
  (dom/div {:className "desktop"}
    (dom/div {:class :root}
      "Some text")))
```

```css
.desktop .my_ns_core_example-component_root {
  background-color: tomato;
}
```

### User-defined variables in colocated styles

Om-css compiles and generates the CSS at macro-expansion time. Because ClojureScript macros are written in Clojure, any functions or variables used inside the colocated style must be declared in a `.clj` or `.cljc` file (commonly a `.cljc` file is preferred so that you can also refer to those variables in your ClojureScript code). An example is presented below.

```clojure
(ns my-ns.constants)

(def some-style {:margin "0px"})

(ns my-ns.core
  (:require [om-css.core :as oc :refer-macros [defui]]
            [om-css.dom :as dom]
            [my-ns.constants :as c]))

(defui Foo
   static oc/Style
   (style [_]
     [[:.root {:background-color "tomato"}]
      [:.section (merge c/some-style ;; <= notice this
                   {:background-color :green})]])
   Object
   (render [this]
     (dom/div {:class :root} "div with class :root"
       (dom/section {:class :section} "section with class :section"))))
```

Check out more examples [here](./src/devcards/om_css/devcards/core.cljs).


## Copyright & License

Copyright © 2016 Ladder Financial, Inc.

Distributed under the Eclipse Public License (see [LICENSE](./LICENSE)).
