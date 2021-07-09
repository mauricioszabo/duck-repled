(ns duck-repled.autocomplete-resolvers
  (:require [duck-repled.connect :as connect]
            [com.wsscode.pathom3.connect.operation :as pco]
            [clojure.string :as str]
            [duck-repled.template :as t]
            [promesa.core :as p]
            [duck-repled.repl-protocol :as repl]))

(def ^:private valid-prefix #"/?([a-zA-Z0-9\-.$!?\/><*=\?_:]+)")
;
; (defn- normalize-results [result]
;   (vec (some->> result
;                 helpers/parse-result
;                 :result
;                 (map (fn [c] {:type :function :candidate c})))))

(def ^:private re-char-escapes
  (->> "\\.*+|?()[]{}$^"
       set
       (map (juxt identity #(str "\\" %)))
       (into {})))
(defn- re-escape [prefix] (str/escape (str prefix) re-char-escapes))

(connect/defresolver clojure-complete [{:keys [text/contents repl/evaluator repl/namespace]}]
  {::pco/output [:completions/var]}
  (p/let [prefix (->> contents (re-seq valid-prefix) last last re-escape re-pattern)
          cmd `(let [collect# #(map (comp str first) (%1 %2))
                     refers# (collect# clojure.core/ns-map *ns*)
                     from-ns# (->> (clojure.core/ns-aliases *ns*)
                                   (mapcat (fn [[k# v#]]
                                             (map #(str k# "" %))
                                             (collect# ns-publics v#))))]
                 (->> refers#
                      (concat from-ns#)
                      (filter #(re-find :prefix-regexp %))))
          res (repl/eval evaluator
                         (t/template cmd {:prefix-regexp prefix})
                         {:namespace namespace})]
    {:completions/var (mapv (fn [res]
                              {:text/contents res
                               :completion/type :function})
                            (:result res))}))

(connect/defresolver clojure-keyword [{:keys [text/contents repl/evaluator repl/namespace]}]
  {::pco/output [:completions/keyword]}
  (p/let [prefix (->> contents (re-seq valid-prefix) last last re-escape re-pattern)
          cmd `(let [^java.lang.reflect.Field field# (.getDeclaredField clojure.lang.Keyword "table")]
                 (.setAccessible field# true)
                 (->> (.get field# nil)
                      (map #(.getKey %))))
          {:keys [result]} (repl/eval evaluator (t/template cmd {}))]
    {:completions/keyword
     (->> result
          (map #(str ":" %))
          (filter #(re-find prefix %))
          (mapv (fn [elem] {:text/contents elem :completion/type :keyword})))}))

; (re-find
;  (->> ":gen" (re-seq valid-prefix) last last re-escape re-pattern)
;  ":gen-class")

; (repl/eval evaluator (t/template cmd {}))

(def resolvers [clojure-complete clojure-keyword])
