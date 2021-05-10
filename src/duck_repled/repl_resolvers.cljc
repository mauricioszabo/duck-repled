(ns duck-repled.repl-resolvers
  (:require [clojure.string :as str]
            [duck-repled.connect :as connect]
            [com.wsscode.pathom3.connect.operation :as pco]
            [duck-repled.repl-protocol :as repl]
            [promesa.core :as p]))

(connect/defresolver repl-eval [{:repl/keys [evaluator namespace]
                                 :text/keys [contents range]}]
  {::pco/input [:repl/evaluator :text/contents
                (pco/? :repl/namespace) (pco/? :text/range)]
   ::pco/output [:repl/result :repl/error]}

  (p/let [opts (cond-> {}
                       namespace (assoc :namespace namespace)
                       range (assoc :row (-> range first first)
                                    :col (-> range first second)))
          result (repl/eval evaluator contents opts)]
    (if (:error result)
      {:repl/error result}
      {:repl/result result})))

(def resolvers [repl-eval])
