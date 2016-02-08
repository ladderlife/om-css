# CSS in Components [![Clojars Project](https://img.shields.io/clojars/v/com.ladderlife/om-css.svg)](https://clojars.org/com.ladderlife/om-css)


## How to use it:

To get started, `:require` om-css in your project:

```clojure
(ns my-ns.core
  (:require [om-css.core :as oc :refer-macros [defui]]
            [om-css.dom :as dom])
```

When using om-css's `defui`, using `om-css.dom` is required.

Define components as you might do in Om Next. Implement om-css.core's `Style` protocol. Styles must use [Garden](https://github.com/noprompt/garden)'s syntax. An example is displayed below:

```clojure
(defui Component
  oc/Style
  (style [_]
    [[:.root {:color "#FFFFF"}]
     [:.section (merge {} ;;style-1
                  {:background-color :green})]])
  Object
  (render [this]
    (dom/div {:id "ns-test"}
      (dom/div {:class :root} "div with class :root"
        (dom/section {:class :section} "section with class :section"
          (dom/p {:className "preserved"} "paragraph with class \"preserved\""))))))
```

Styles written in components are written to a CSS file. You can add an option `:css-output-to` to the Clojurescript compiler with the output path for your styles. [Here](./scripts/figwheel.clj#L15)'s an example.

Check out the file `src/devcards/om_css/devcards/core.cljs` for more examples.


## High level ideas
- CSS/SASS is not a good programming language and composition, reuse, and
  abstraction should happen in clojure side.
- A large CSS codebase is very difficult to reason about and CSS overrides are very difficult to
  reason about
- Components are a great way to do composition, reuse, etc

## Concrete idea
- Write CSS that is one to one with components. Basically CSS rules that only apply to your
  component and nothing else.

## Implementation Idea
- Each css rule should be prefixed by a namespace path and the component name.

```clojure
(defui Foo
  Object
  (render [this]
    (dom/div {:class "ladder_components_Foo"}
      (dom/div {:class "ladder_components_Foo--section"} "section"))))
```

```css
ladder_components_Foo {
  some: style;
}

ladder_components_Foo--section {
  more: style;
}
```

## Convenience / syntactic sugar

- Writing these full qualified names is prone to mistakes and is tedious.
- We want to be able to enforce a correspondence between style selectors and component class names.
- We also want to collocate style with components
- CSS in CLJS library https://github.com/noprompt/garden

Ultimately we want to get to something like this

```clojure
(ns ladder.components
  (:require [ladder.css :as css]))

(defui Foo
  Style
  (style []
    [[:.root {:color (:blue css)}]
     [:.section (merge css/default-section
                     {:background-color :green})]])
  Object
  (render [this]
    (dom/div {:class :root}
      (dom/div {:class :section} "section"))
```


## Annoyances

- We'll want to run our css through https://github.com/postcss/autoprefixer
- This is a js library
- Solutions: Compiler could run cljs in nodejs
- Solutions: We could setup / hit autoprefixer from clojure over HTTP
- Solutions: We could output a css file and have webpack compile it
