(ns duck-repled.repl-resolvers
  (:require [clojure.string :as str]
            [duck-repled.connect :as connect]
            [com.wsscode.pathom3.connect.operation :as pco]
            [duck-repled.repl-protocol :as repl]
            [duck-repled.template :refer [template]]
            [duck-repled.editor-helpers :as helpers]
            [clojure.walk :as walk]
            [promesa.core :as p]))

(connect/defresolver get-right-repl [{:repl/keys [kind evaluators]}]
  {::pco/output [:repl/evaluator :repl/cljs]}

  (let [{:keys [clj cljs]} evaluators]
    (cond
      (not= kind :cljs) {:repl/evaluator clj :repl/clj clj}
      (nil? clj) {:repl/evaluator cljs}
      :else {:repl/clj clj :repl/evaluator cljs})))

(connect/defresolver repl-eval [env {:repl/keys [evaluator namespace]
                                     :text/keys [contents range]
                                     :editor/keys [filename]}]
  {::pco/input [:repl/evaluator :text/contents
                (pco/? :editor/filename) (pco/? :repl/namespace) (pco/? :text/range)]
   ::pco/output [:repl/result :repl/error]}

  (p/let [params (pco/params env)
          opts (cond-> (dissoc params :repl/template)
                       namespace (assoc :namespace namespace)
                       filename (assoc :filename filename)
                       range (-> (update :row #(or % (-> range first first)))
                                 (update :col #(or % (-> range first second)))))
          code (if-let [t (:repl/template params)]
                 (template t {:repl/code (symbol contents)})
                 contents)
          result (repl/eval evaluator code opts)]
    (if (:error result)
      {:repl/error result}
      {:repl/result result})))

(connect/defresolver fqn-var
  [{:keys [repl/namespace text/current-var text/contents repl/evaluator]}]
  {::pco/input [:repl/namespace :repl/evaluator
                (pco/? :text/current-var) (pco/? :text/contents)]
   ::pco/output [:var/fqn]}

  (let [contents (or (:text/contents current-var) contents)
        [_ var] (helpers/current-var (str contents) [0 0])]
    (when (and var (= var contents))
      (p/let [{:keys [result]} (repl/eval evaluator
                                          (str "`" contents)
                                          {:namespace (str namespace)})]
        {:var/fqn result}))))

(defn- eval-for-meta [evaluator var-name namespace]
  (p/let [{:keys [result]} (repl/eval evaluator
                                      (template `(meta ::current-var)
                                                {::current-var (->> var-name
                                                                    (str "#'")
                                                                    symbol)})
                                      {:namespace (str namespace)})]
    (when result {:var/meta result})))

(connect/defresolver meta-for-var
  [{:keys [repl/namespace editor/current-var repl/evaluator config/repl-kind]}]
  {::pco/input [:repl/namespace :editor/current-var :repl/evaluator
                (pco/? :config/repl-kind)]
   ::pco/output [:var/meta]
   ::pco/priority 1}
  (p/let [meta (eval-for-meta evaluator (:text/contents current-var) namespace)]
    (if (= :clje repl-kind)
      (walk/postwalk #(cond-> %
                              (and (tagged-literal? %) (-> % .-tag (= 'erl)))
                              .-form)
                     meta)
      meta)))

(connect/defresolver meta-for-clj-var
  [{:keys [var/fqn repl/clj repl/kind]}]
  {::pco/output [:var/meta]}

  (when (= :cljs kind)
    (eval-for-meta clj fqn 'user)))

; TODO: Somehow, test this
(pco/defresolver spec-for-var [{:keys [var/fqn repl/evaluator]}]
  {::pco/output [:var/spec]}

  (p/let [res (repl/eval evaluator "(require 'clojure.spec.alpha)")]
    (when-not (:error res)
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
        (when result {:var/spec result})))))

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
