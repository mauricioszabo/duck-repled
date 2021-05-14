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

#_
(pco/defresolver spec-for-var
  [{:keys [var/fqn repl/aux]}]
  {::pco/output [:var/spec]}

  (p/let [{:keys [result]}
          (eval/eval
            aux
            (str "(clojure.core/let [s (clojure.spec.alpha/get-spec '" fqn ")"
              "                   fun #(clojure.core/some->> (% s) clojure.spec.alpha/describe)]"
              " (clojure.core/when s"
              "   (clojure.core/->> [:args :ret :fn]"
              "      (clojure.core/map (clojure.core/juxt clojure.core/identity fun))"
              "      (clojure.core/filter clojure.core/second)"
              "      (clojure.core/into {}))))"))]
    (when result {:var/spec result})))


(def resolvers [get-right-repl repl-eval fqn-var
                meta-for-var meta-for-clj-var])
