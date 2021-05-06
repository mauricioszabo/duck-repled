(ns duck-repled.schemas
  (:refer-clojure :exclude [range])
  (:require [malli.core :as m]
            [malli.error :as e]
            [malli.util :as mu]))

(def ^private pos [:cat int? int?])
(def ^private range [:cat [:schema pos] [:schema pos]])
(def ^private editor-data [:map
                           [:contents string?]
                           [:filename {:maybe true} [:maybe string?]]
                           [:range range]])
(def ^private range-and-content [:cat [:schema range] string?])
(def ^private top-blocks [:vector range-and-content])

(def registry
  {:editor/data (m/schema editor-data)
   :editor/contents (m/schema string?)
   :editor/filename (m/schema string?)
   :editor/range (m/schema range)
   :editor/ns-range (m/schema range)
   :editor/namespace (m/schema string?)
   :editor/top-blocks (m/schema top-blocks)
   :editor/current-var (m/schema string?)
   :editor/current-var-range (m/schema range)

   :cljs/required? (m/schema boolean?)
   :repl/namespace (m/schema simple-symbol?)
   :map (:map (m/base-schemas))})

#_
(validate! [:editor/top-blocks]
           {:editor/top-blocks
            [[[[0 0] [0 1]] "()"] [[[0 3] [0 4]] "()"]]})

(def explainer
  (memoize (fn [schemas]
             (let [mapped (apply vector :map schemas)]
                (m/explainer mapped {:registry registry})))))

(defn validate!
  ([schemas value]
   (validate! schemas value "Value does not match schema: "))
  ([schemas value explanation]
   (let [explain (explainer schemas)
         exp-error (explain value)
         exp (e/humanize exp-error)]
     (when exp
       (throw (ex-info (str explanation exp)
                       {:human-error exp :details exp-error})))
     value)))
