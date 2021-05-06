(ns duck-repled.schemas
  (:refer-clojure :exclude [range])
  (:require [malli.core :as m]
            [malli.error :as e]
            [malli.util :as mu]))

(def pos [:cat int? int?])
(def range [:cat [:schema pos] [:schema pos]])
(def editor-data [:map
                  [:contents string?]
                  [:filename string?]
                  [:range range]])
(def registry
  {:editor/data (m/schema editor-data)
   :editor/contents (m/schema string?)
   :editor/filename (m/schema string?)
   :editor/range (m/schema range)
   :map (:map (m/base-schemas))})

#_
(e/humanize
 (m/explain [:map :editor/contents :editor/range]
            {:editor/contents 1
             :editor/range [ [1 1]]}
            {:registry registry}))

#_
(validate! [[:map :editor/contents :editor/range]]
           {:editor/contents ""
             :editor/range [ [1 1] [1 1]]})

(def explainer
  (memoize (fn [schemas]
             (let [mapped (apply vector :map schemas)]
                (m/explainer mapped {:registry registry})))))

(defn validate! [schemas value]
  (let [explain (explainer schemas)
        exp-error (explain value)
        exp (e/humanize exp-error)]
    (when exp
      (throw (ex-info (str "Value does not match schema: " exp)
                      {:human-error exp :details exp-error})))
    value))

#_
(validate! [:editor/contents :editor/range :editor/filename]
           {:editor/contents ""
            :editor/range [[1 1] [1 1]]})
#_
(let [v (explainer [:editor/data])]
  (v {}))
#_
(-> #{}
    (conj (mu/merge :editor/data :editor/data {:registry registry}))
    (conj (mu/merge :editor/data :editor/data {:registry registry}))
    (conj (mu/merge :editor/data :editor/data {:registry registry})))
; #{
;   (mu/merge :editor/data :editor/data {:registry registry})
;   (mu/merge :editor/data :editor/data {:registry registry})}
;
; (m/validator)
; #_
; (-> :editor/data
;     (m/explain {:editor/data {:contents "foo" :filename "" :range [[1 1] [1 1]]}}
;                {:registry registry})
;     e/humanize)
;
; #_
(-> (mu/merge :editor/data nil {:registry registry})
    (m/explain {:editor/data {:contents 10 :filename "" :range [[1 1] [1 1]]}}))
               ; {:registry registry})
    ; e/humanize)
