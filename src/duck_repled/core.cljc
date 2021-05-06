(ns duck-repled.core
  (:require [duck-repled.editor-resolvers :as editor]
            [com.wsscode.pathom3.connect.indexes :as indexes]
            [com.wsscode.pathom3.interface.async.eql :as eql]
            [com.wsscode.pathom3.plugin :as plugin]
            [com.wsscode.pathom3.connect.built-in.plugins :as plugins]
            [duck-repled.schemas :as schemas]))

(def resolvers (concat editor/resolvers))
(def ^:private env (-> resolvers
                       indexes/register
                       (plugin/register (plugins/attribute-errors-plugin))))

(defn eql
  ([query]
   (eql/process env query))
  ([seed query]
   (schemas/validate! (keys seed) seed)
   (eql/process env seed query)))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))
