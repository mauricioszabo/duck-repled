(ns duck-repled.repl-resolvers
  (:require [clojure.string :as str]
            [duck-repled.connect :as connect]
            [com.wsscode.pathom3.connect.operation :as pco]
            [duck-repled.repl-protocol :as repl]
            [promesa.core :as p]))

(pco/defresolver repl-eval [{:repl/keys [evaluator code namespace]}]
  {::pco/output [:repl/result :repl/error]}

  (p/let [result (repl/eval evaluator code {:namespace namespace})]
    (if (:error result)
      {:repl/error result}
      {:repl/result result})))

(def resolvers [repl-eval])

; (defonce plan-cache* (atom {}))
;

;
; (s/defn eql :- js/Promise
;   "Queries the Pathom graph for the REPLs"
;   [params :- {(s/optional-key :editor-state) schemas/EditorState
;               (s/optional-key :callbacks)    s/Any}
;    query]
;   (let [params (cond-> params
;                  (-> params :callbacks nil?)
;                  (assoc :callbacks (-> params :editor-state deref :editor/callbacks)))]
;     (p.a.eql/process (merge env params) query)))
