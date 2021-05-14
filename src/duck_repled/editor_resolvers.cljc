(ns duck-repled.editor-resolvers
  (:require [clojure.string :as str]
            [duck-repled.schemas :as schemas]
            [duck-repled.connect :as connect]
            [com.wsscode.pathom3.connect.operation :as pco]
            [duck-repled.editor-helpers :as editor-helpers]))

(connect/defresolver seed-data [{:keys [seed]} _]
  {::pco/output (->> schemas/registry keys (remove #{:map}) vec)
   ::pco/priority 99}
  seed)

(connect/defresolver separate-data [{editor-data :editor/data}]
  {::pco/output [:editor/contents :editor/filename :editor/range]}

  ; (when-let [editor-data (-> env :seed :editor/data)]
  (let [file (:filename editor-data)]
    (cond-> {:editor/contents (:contents editor-data)
             :editor/range (:range editor-data)}
            file (assoc :editor/filename file))))

(connect/defresolver top-blocks [{:editor/keys [contents]}]
  {:editor/top-blocks (editor-helpers/top-blocks contents)})

(connect/defresolver namespace-from-editor-data [{:editor/keys [top-blocks range]
                                                  :as inputs}]
  {::pco/input [:editor/top-blocks :editor/range]
   ::pco/output [{:editor/ns [:text/contents :text/range]}]}

  (when-let [[range ns] (editor-helpers/ns-range-for top-blocks (first range))]
    {:editor/ns {:text/contents (str ns) :text/range range}}))

(connect/defresolver current-top-block [{:editor/keys [top-blocks range]
                                         :as inputs}]
  {::pco/input [:editor/top-blocks :editor/range
                (pco/? :repl/evaluator) (pco/? {:editor/ns [:text/contents]})]
   ::pco/output [{:editor/top-block [:text/contents :text/range :repl/evaluator]}]}

  (when-let [[range text] (editor-helpers/top-block-for top-blocks (first range))]
    {:editor/top-block {:text/contents text :text/range range}}))

(connect/defresolver current-block [{:editor/keys [contents range]
                                     :as inputs}]
  {::pco/input [:editor/contents :editor/range
                (pco/? :repl/evaluator) (pco/? {:editor/ns [:text/contents]})]
   ::pco/output [{:editor/block [:text/contents :text/range :repl/evaluator]}]}

  (when-let [[range text] (editor-helpers/block-for contents (first range))]
    {:editor/block {:text/contents text :text/range range}}))

(connect/defresolver current-selection [{:editor/keys [contents range]
                                         :as inputs}]
  {::pco/input [:editor/contents :editor/range
                (pco/? :repl/evaluator) (pco/? {:editor/ns [:text/contents]})]
   ::pco/output [{:editor/selection [:text/contents :text/range :repl/evaluator]}]}

  (when-let [text (editor-helpers/text-in-range contents range)]
    {:editor/selection {:text/contents text :text/range range}}))

; (connect/defresolver default-namespaces [env {:keys [repl/kind]}]
;   {:repl/namespace (if (= :cljs kind) 'cljs.user 'user)})
;
; (connect/defresolver namespace-from-editor [inputs]
;   {::pco/input [{:editor/ns [:text/contents]}]
;    ::pco/output [:repl/namespace] ::pco/priority 1}
;   {:repl/namespace (-> inputs :editor/ns :text/contents symbol)})

(connect/defresolver resolver-for-ns [inputs]
  {::pco/input [(pco/? {:editor/ns [:text/contents]})
                (pco/? :repl/kind)]
   ::pco/output [:repl/namespace]}

  (let [contents (-> inputs :editor/ns :text/contents)
        kind (:repl/kind inputs)]
    (cond
      contents {:repl/namespace (-> inputs :editor/ns :text/contents symbol)}
      (nil? kind) nil
      (= :cljs kind) {:repl/namespace 'cljs.user}
      :not-cljs {:repl/namespace 'user})))

; (connect/defresolver not-clj-repl-kind [{:config/keys [repl-kind]}]
;   {::pco/output [:repl/kind] ::pco/priority 2}
;
;   (when (not= :clj repl-kind)
;     {:repl/kind repl-kind}))
;
; (connect/defresolver repl-kind-from-config [{:config/keys [eval-as]}]
;   {::pco/output [:repl/kind] ::pco/priority 1}
;
;   (case eval-as
;     :clj {:repl/kind :clj}
;     :cljs {:repl/kind :cljs}
;     nil))
;     ; ::pco/unknown-value))
;
; (connect/defresolver repl-kind-from-config-and-file
;   [{:keys [config/eval-as editor/filename]}]
;   {::pco/output [:repl/kind]}
;
;   (prn :CHECKING-PRIORITY-0)
;   (let [cljs-file? (str/ends-with? filename ".cljs")
;         cljc-file? (or (str/ends-with? filename ".cljc")
;                        (str/ends-with? filename ".cljx"))]
;     (case eval-as
;       :prefer-clj {:repl/kind (if cljs-file? :cljs :clj)}
;       :prefer-cljs {:repl/kind (if (and (not cljs-file?) (not cljc-file?))
;                                  :clj
;                                  :cljs)}
;       nil)))

(connect/defresolver resolve-repl-kind
  [{:keys [config/repl-kind config/eval-as editor/filename]}]
  {::pco/input [(pco/? :config/repl-kind) (pco/? :config/eval-as)
                (pco/? :editor/filename)]
   ::pco/output [:repl/kind]}

  (cond
    (and repl-kind (not= :clj repl-kind))
    {:repl/kind repl-kind}

    (#{:clj :cljs} eval-as)
    {:repl/kind eval-as}

    :else
    (let [cljs-file? (str/ends-with? filename ".cljs")
          cljc-file? (or (str/ends-with? filename ".cljc")
                         (str/ends-with? filename ".cljx"))]
      (case eval-as
        :prefer-clj {:repl/kind (if cljs-file? :cljs :clj)}
        :prefer-cljs {:repl/kind (if (and (not cljs-file?) (not cljc-file?))
                                   :clj
                                   :cljs)}
        nil))))

(connect/defresolver var-from-editor
  [{:editor/keys [contents range]}]
  {::pco/output [{:editor/current-var [:text/contents :text/range]}]}

  (when-let [[range curr-var] (editor-helpers/current-var contents (first range))]
    {:editor/current-var {:text/contents curr-var
                          :text/range range}}))

; (pco/defresolver all-namespaces
;   [env {:keys [repl/clj]}]
;   {::pco/output [{:repl/namespaces [:repl/namespace]}]}
;
;   (p/let [f (-> (pco/params env) :filter)
;           {:keys [result]} (eval/eval clj "(clojure.core/mapv clojure.core/ns-name (clojure.core/all-ns))")]
;     {:repl/namespaces (cond->> (map (fn [n] {:repl/namespace n}) result)
;                         f (filter (fn [n]
;                                     (-> n :repl/namespace str
;                                         (str/starts-with? f)))))}))
;
; (pco/defresolver need-cljs-from-config [{:editor/keys [config]}]
;   {::pco/output [:cljs/required?]}
;
;   (case (:eval-mode config)
;     :clj {:cljs/required? false}
;     :cljs {:cljs/required? true}
;     nil))
;
; (pco/defresolver all-vars-in-ns
;   [_ {:repl/keys [namespace aux]}]
;   {::pco/output [{:namespace/vars [:var/fqn]}]}
;
;   (p/let [{:keys [result]} (eval/eval aux (str "(clojure.core/ns-interns '" namespace ")"))]
;     {:namespace/vars (map (fn [v] {:var/fqn (symbol namespace v)})
;                        (keys result))}))
;
; (pco/defresolver cljs-env [{:keys [editor-state]} {:keys [repl/clj]}]
;   {::pco/output [:cljs/env]}
;
;   (when-let [cmd (-> @editor-state :repl/info :cljs/repl-env)]
;     (p/let [{:keys [result]} (eval/eval clj (str cmd))]
;       {:cljs/env result})))
;
; (def ^:private kondo-cache (atom {:cache nil :when 0}))
;
; (defn- run-kondo [dirs]
;   (let [p      (p/deferred)
;         buffer (atom "")
;         cp     (spawn "clj-kondo"
;                  (clj->js (concat ["--lint"]
;                             dirs
;                             ["--config"
;                              "{:output {:analysis true :format :json}}"])))]
;     (.. cp -stdout (on "data" #(swap! buffer str %)))
;     (. cp on "error" #(p/resolve! p nil))
;     (. cp on "close" #(p/resolve! p @buffer))
;     p))
;
; (defn- run-kondo-maybe [dirs]
;   (let [curr-time (long (new js/Date))
;         {:keys [when cache]} @kondo-cache]
;     (if (< (- curr-time 6000) when)
;       cache
;       (p/finally (run-kondo dirs)
;         (fn [res]
;           (reset! kondo-cache {:when (int (new js/Date)) :cache res}))))))
;
; (pco/defresolver analysis-from-kondo
;   [{:keys [editor-state]} {:keys [editor/config]}]
;   {::pco/output [:kondo/analysis]}
;
;   (when-not editor-state
;     (p/let [kondo (run-kondo-maybe (:project-paths config))]
;       {:kondo/analysis (some-> (.parse js/JSON kondo) .-analysis)})))
;
; (defn- get-from-ns-usages [analysis namespace ns-part]
;   (-> analysis
;       (aget "namespace-usages")
;       (->> (filter (fn [^js %] (and (-> % .-from (= (str namespace)))
;                                     (-> % .-alias (= ns-part))))))
;       first
;       (some-> .-to)))
;
; (defn- get-from-var-usages [analysis namespace current-var]
;   (-> analysis
;       (aget "var-usages")
;       (->> (filter (fn [^js %] (and (-> % .-from (= (str namespace)))
;                                     (-> % .-name (= current-var))))))
;       first
;       (some-> .-to)))
;
; (defn- get-from-definitions [analysis namespace current-var]
;   (-> analysis
;       (aget "var-definitions")
;       (->> (filter (fn [^js %] (and (-> % .-ns (= (str namespace)))
;                                     (-> % .-name (= current-var))))))
;       first))
;
; (pco/defresolver fqn-from-kondo
;   [{:keys [kondo/analysis editor/current-var repl/namespace]}]
;   {::pco/output [:var/fqn]}
;
;   (let [as-sym     (symbol current-var)
;         ns-part    (clojure.core/namespace as-sym)
;         without-ns (name as-sym)
;         finding    (if ns-part
;                      (get-from-ns-usages analysis namespace ns-part)
;                      (or (get-from-var-usages analysis namespace current-var)
;                          (some-> (get-from-definitions analysis namespace current-var)
;                                  .-ns)))]
;     (when finding
;       {:var/fqn (symbol finding without-ns)})))
;
; (pco/defresolver meta-from-kondo
;   [{:keys [kondo/analysis var/fqn]}]
;   {::pco/output [:var/meta]}
;
;   (let [ns-part    (namespace fqn)
;         without-ns (name fqn)]
;     (when-let [^js res (get-from-definitions analysis ns-part without-ns)]
;       {:var/meta (cond-> {:file   (.-filename res)
;                           :line   (.-row res)
;                           :column (.-col res)
;                           :ns     (.-ns res) :name (.-name res)}
;                    (.-doc res) (assoc :doc (.-doc res))
;                    (.-test res) (assoc :test (.-test res)))})))
;

(def resolvers [seed-data separate-data top-blocks
                namespace-from-editor-data
                resolver-for-ns
                ; default-namespaces namespace-from-editor
                var-from-editor current-top-block current-block current-selection

                ; repl-kind-from-config not-clj-repl-kind repl-kind-from-config-and-file
                resolve-repl-kind])
