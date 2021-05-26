(ns duck-repled.core
  (:require [com.wsscode.pathom3.connect.indexes :as indexes]
            [com.wsscode.pathom3.interface.async.eql :as eql]
            [com.wsscode.pathom3.plugin :as plugin]
            [com.wsscode.pathom3.connect.built-in.plugins :as plugins]
            [duck-repled.schemas :as schemas]
            [duck-repled.editor-resolvers :as editor]
            [duck-repled.repl-resolvers :as repl]
            [duck-repled.definition-resolvers :as def]
            [com.wsscode.pathom3.connect.operation :as pco]))

(def ^:private resolvers (concat editor/resolvers
                                 repl/resolvers
                                 def/resolvers))
(def ^:private env (-> resolvers
                       indexes/register
                       (plugin/register (plugins/attribute-errors-plugin))))

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


; (pco/defresolver default-namespaces [env {:keys [repl/kind]}]
;   {::pco/output [:repl/namespace] ::pco/priority 0}
;
;   (prn :PRIORITY-0))
;
; (pco/defresolver namespace-from-editor [inputs]
;   {::pco/input [{:editor/ns [:text/contents]}]
;    ::pco/output [:repl/namespace]
;    ::pco/priority 1}
;
;   (prn :PRIORITY-1)
;   {:repl/namespace (-> inputs :editor/ns :text/contents symbol)})
;
; (pco/defresolver repl-kind-from-config [{:config/keys [eval-as]}]
;   {::pco/output [:repl/kind] ::pco/priority 1}
;
;   {:repl/kind eval-as})
;
; (pco/defresolver seed-data [{:keys [seed]} _]
;   {::pco/output (->> schemas/registry keys (remove #{:map}) vec)
;    ::pco/priority 99}
;   seed)
;
; #_
; (-> [seed-data
;      repl-kind-from-config
;      default-namespaces namespace-from-editor]
;     indexes/register
;     (plugin/register (plugins/attribute-errors-plugin))
;     (assoc :seed {:repl/kind :clj
;                   :editor/ns {:text/contents "some-ns"}})
;     (eql/process [:repl/namespace]))
