(ns duck-repled.schemas
  (:refer-clojure :exclude [range])
  (:require [malli.core :as m]
            [malli.error :as e]
            [malli.util :as mu]
            [duck-repled.repl-protocol :as repl]))

(def ^:private pos [:cat int? int?])
(def ^:private range [:cat [:schema pos] [:schema pos]])
(def ^:private editor-data [:map
                            [:contents string?]
                            [:filename {:maybe true} [:maybe string?]]
                            [:range range]])
(def ^:private range-and-content [:cat [:schema range] string?])
(def ^:private top-blocks [:vector range-and-content])
(def ^:private contents [:map
                         [:text/contents string?]
                         [:text/range range]])
(def registry
  {:editor/data (m/schema editor-data)
   :editor/contents (m/schema string?)
   :editor/filename (m/schema string?)
   :editor/range (m/schema range)
   :editor/namespace (m/schema string?)
   :editor/top-blocks (m/schema top-blocks)

   :editor/current-var (m/schema contents)
   :editor/ns (m/schema contents)
   :editor/top-block (m/schema contents)
   :editor/block (m/schema contents)
   :editor/selection (m/schema contents)
   ; :editor/current-var-range (m/schema range)

   :text/contents (m/schema string?)
   :text/range (m/schema range)

   :config/repl-kind (m/schema keyword?)
   :config/eval-as (m/schema [:enum :clj :cljs :prefer-clj :prefer-cljs])

   :repl/kind (m/schema keyword?)
   :repl/namespace (m/schema simple-symbol?)
   :repl/evaluator (m/schema any?)
   :repl/clj (m/schema any?)
   :repl/cljs (m/schema any?)
   :repl/code (m/schema string?)
   :repl/result (m/schema [:map [:result any?]])
   :repl/error (m/schema [:map [:error any?]])

   :map (:map (m/base-schemas))})

#_
(validate! [:config/eval-as]
           {:config/eval-as :clja})

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
