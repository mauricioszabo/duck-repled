(ns duck-repled.core
  (:require [com.wsscode.pathom3.connect.indexes :as indexes]
            [com.wsscode.pathom3.interface.async.eql :as eql]
            [com.wsscode.pathom3.plugin :as plugin]
            [com.wsscode.pathom3.connect.built-in.plugins :as plugins]
            [duck-repled.schemas :as schemas]
            [duck-repled.editor-resolvers :as editor]
            [duck-repled.repl-resolvers :as repl]))

(def ^:private resolvers (concat editor/resolvers
                                 repl/resolvers))
(def ^:private env (-> resolvers
                       indexes/register
                       (plugin/register (plugins/attribute-errors-plugin))))

(def ^:private env-keys [:editor/data :repl/evaluators])
(defn eql
  ([query]
   (eql/process env query))
  ([seed query]
   (schemas/validate! (keys seed) seed)
   (eql/process (assoc env :seed seed) query)))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

#_
(eql/process (assoc env :bar {:foo 10}) [:foo])
