(ns duck-repled.tests
  (:require [duck-repled.editor-test]
            [duck-repled.repl-test]
            [clojure.test :as test]))

(defn main [ & args]
  #?(:cljs
     (when (= args ["--test"])
       (defmethod test/report [::test/default :summary] [{:keys [fail error]}]
         (if (= 0 fail error)
           (js/process.exit 0)
           (js/process.exit 1)))
       (test/run-all-tests)))
  (prn :loaded))

; (ns example
;   (:require [com.wsscode.pathom3.connect.indexes :as indexes]
;             [com.wsscode.pathom3.interface.async.eql :as eql]
;             [com.wsscode.pathom3.connect.operation :as pco]
;             [com.wsscode.pathom3.plugin :as plugin]
;             [com.wsscode.pathom3.connect.built-in.plugins :as plugins]))
;
; (pco/defresolver resolver-editor-ns [{:keys [editor/data]}]
;   {:editor/ns {:text/contents (->> data
;                                    :contents
;                                    (re-seq #"ns (.*)\)")
;                                    first
;                                    second)}})
;
; (pco/defresolver seed-data [{:keys [seed]} _]
;   {::pco/output [:config/eval-as :editor/data :config/repl-kind :repl/kind]
;    ::pco/priority 99}
;   seed)
;
; (pco/defresolver p0 [env {:keys [repl/kind]}]
;   {::pco/output [:repl/namespace]
;    ::pco/priority 0}
;
;   (prn :NS-FROM-DEFAULT kind))
;   ; {:repl/namespace (if (= :cljs kind) 'cljs.user 'user)})
;
; (pco/defresolver p1 [inputs]
;   {::pco/input [{:editor/ns [:text/contents]}]
;    ::pco/output [:repl/namespace]
;    ::pco/priority 1}
;
;   (prn :NS-FROM-EDITOR inputs)
;   {:repl/namespace (-> inputs :editor/ns :text/contents symbol)})
;
; #_
; (eql/process (-> [seed-data resolver-editor-ns p0 p1]
;                  indexes/register
;                  (plugin/register (plugins/attribute-errors-plugin))
;                  (assoc :seed {:editor/data {:contents "(ns foo)\nflavor"}
;                                :repl/kind :clj
;                                :config/eval-as :prefer-clj}))
;          [:editor/ns :repl/namespace])
