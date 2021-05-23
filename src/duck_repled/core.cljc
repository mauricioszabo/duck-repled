(ns duck-repled.core
  (:require [com.wsscode.pathom3.connect.indexes :as indexes]
            [com.wsscode.pathom3.interface.async.eql :as eql]
            [com.wsscode.pathom3.plugin :as plugin]
            [com.wsscode.pathom3.connect.built-in.plugins :as plugins]
            [duck-repled.schemas :as schemas]
            [duck-repled.editor-resolvers :as editor]
            [duck-repled.repl-resolvers :as repl]
            [duck-repled.definition-resolvers :as def]
            [promesa.core :as p]
            [com.wsscode.pathom3.connect.operation :as pco]
            [clojure.set :as set]))

(def ^:private original-resolvers (vec (concat editor/resolvers
                                               repl/resolvers
                                               def/resolvers)))

(defn- gen-resolver-fun [fun outputs]
  (fn [_ input]
    (p/let [result (fun input)]
      (schemas/validate! (keys result)
                         result
                         (str "Invalid schema on custom resolver outputing " outputs)))))

(defn gen-eql
  ([] (gen-eql original-resolvers))
  ([resolvers]
   (fn query
     ([query] (query {} query))
     ([seed query]
      (schemas/validate! (keys seed) seed)
      (-> resolvers
          indexes/register
          (plugin/register (plugins/attribute-errors-plugin))
          (assoc :seed seed)
          (eql/process query))))))

(defn add-resolver
  ([config fun] (add-resolver original-resolvers config fun))
  ([resolvers {:keys [inputs outputs priority] :as config} fun]
   (when-let [errors (schemas/explain-add-resolver config)]
     (throw (ex-info "Input to add-resolver is invalid" {:errors errors})))

   (-> resolvers
       (conj (pco/resolver (gensym "custom-resolver-")
                           {::pco/input inputs
                            ::pco/output outputs
                            ::pco/priority (or priority 50)}
                           (gen-resolver-fun fun outputs)))
       gen-eql)))

(defn- rename-resolve-out [resolve-out]
  (let [out-ns (namespace resolve-out)
        out-name (name resolve-out)]
    (keyword out-ns (str out-name "-rewrote"))))

(defn- rename-resolvers-that-output [resolvers outputs]
  (let [rewroted-map (zipmap outputs (map rename-resolve-out outputs))]
    (for [resolver resolvers
          :let [resolver-out (-> resolver :config ::pco/output)
                new-out (mapv #(cond-> % (rewroted-map %) rewroted-map)
                              resolver-out)
                fun (:resolve resolver)]]
      (if (= resolver-out new-out)
        resolver
        (pco/resolver (-> resolver :config ::pco/op-name (str "-renamed") symbol)
                      {::pco/input (-> resolver :config ::pco/input)
                       ::pco/output new-out
                       ::pco/priority (-> resolver :config (::pco/priority 0))}
                      (fn [ & args]
                        (p/let [res (apply fun args)]
                          (set/rename-keys res rewroted-map))))))))

(defn compose-resolver
  ([config fun] (compose-resolver original-resolvers config fun))
  ([resolvers {:keys [inputs outputs priority] :as config} fun]
   (when-let [errors (schemas/explain-add-resolver config)]
     (throw (ex-info "Input to add-resolver is invalid" {:errors errors})))

   (let [renamed-resolvers (rename-resolvers-that-output resolvers outputs)
         renamed (map rename-resolve-out outputs)
         inputs (into inputs renamed)
         fun (fn [input]
               (-> input
                   (set/rename-keys (zipmap renamed outputs))
                   fun))]
     (-> renamed-resolvers
         vec
         (conj (pco/resolver (gensym "custom-resolver-")
                             {::pco/input inputs
                              ::pco/output outputs
                              ::pco/priority (or priority 50)}
                             (gen-resolver-fun fun outputs)))
         (gen-eql)))))

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
