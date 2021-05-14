(ns duck-repled.repl-resolvers
  (:require [clojure.string :as str]
            [duck-repled.connect :as connect]
            [com.wsscode.pathom3.connect.operation :as pco]
            [duck-repled.repl-protocol :as repl]
            [duck-repled.template :refer [template]]
            [promesa.core :as p]))

(connect/defresolver get-right-repl [{:repl/keys [kind evaluators]}]
  {::pco/output [:repl/evaluator :repl/cljs]}

  (let [{:keys [clj cljs]} evaluators]
    (cond
      (not= kind :cljs) {:repl/evaluator clj}
      (nil? clj) {:repl/evaluator cljs}
      :embedded-cljs {:repl/clj clj
                      :repl/evaluator cljs})))

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

(connect/defresolver fqn-var
  [{:keys [repl/namespace editor/current-var repl/evaluator]}]
  {::pco/output [:var/fqn]}

  (p/let [{:keys [result]} (repl/eval evaluator
                                      (str "`" (:text/contents current-var))
                                      {:namespace (str namespace)})]
    {:var/fqn result}))

(defn- eval-for-meta [evaluator current-var namespace]
  (p/let [{:keys [result]} (repl/eval evaluator
                                      (template `(meta ::current-var)
                                                {::current-var (->> current-var
                                                                    :text/contents
                                                                    (str "#'")
                                                                    symbol)})
                                      {:namespace (str namespace)})]
    (when result {:var/meta result})))

(connect/defresolver meta-for-var
  [{:keys [repl/namespace editor/current-var repl/evaluator]}]
  {::pco/output [:var/meta] ::pco/priority 1}
  (eval-for-meta evaluator current-var namespace))

(connect/defresolver meta-for-clj-var
  [{:keys [repl/namespace editor/current-var repl/clj repl/kind]}]
  {::pco/output [:var/meta]}

  (when (= :cljs kind)
    (eval-for-meta clj current-var namespace)))

; TODO: Somehow, test this
(pco/defresolver spec-for-var [{:keys [var/fqn repl/evaluator]}]
  {::pco/output [:var/spec]}

  (p/let [{:keys [result]}
          (repl/eval
            evaluator
            (template `(let [s# (clojure.spec.alpha/get-spec ' ::fqn)
                             fun# #(some->> (% s) clojure.spec.alpha/describe)]
                         (when s#
                           (->> [:args :ret :fn]
                                (map (juxt identity fun#))
                                (filter second)
                                (into {}))))
                      {::fqn fqn}))]
    (when result {:var/spec result})))

(pco/defresolver doc-for-var [{:var/keys [fqn meta spec]}]
  {::pco/input [:var/fqn :var/meta (pco/? :var/spec)]
   ::pco/output [:var/doc]}

  {:var/doc
   (str "-------------------------\n"
        fqn "\n"
        (:arglists meta) "\n  "
        (:doc meta)
        (when (map? spec)
          (cond-> "\nSpec\n"
                  (:args spec) (str "  args: " (pr-str (:args spec)) "\n")
                  (:ret spec) (str "  ret: " (pr-str (:ret spec)) "\n")
                  (:fn spec) (str "  fn: " (pr-str (:fn spec))))))})

(def resolvers [get-right-repl repl-eval fqn-var
                meta-for-var meta-for-clj-var
                spec-for-var doc-for-var])
