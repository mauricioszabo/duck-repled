(ns duck-repled.autocomplete-test
  (:require [clojure.test :refer [deftest]]
            [duck-repled.core :as duck]
            [promesa.core :as p]
            [duck-repled.core :as core]
            [duck-repled.repl-helpers :as helpers]
            [duck-repled.repl-protocol :as repl]
            [check.async :refer [testing check async-test]]
            [matcher-combinators.matchers :as m]))

; (time)
; (let [field (.getDeclaredField clojure.lang.Keyword "table")]
;   (.setAccessible field true)
;   (.get field nil))

(deftest autocompletes-core-fns
  (async-test "autocompletion of clojure core"
    (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
            eql (core/gen-eql)]
      (check (eql {:text/contents "re-" :repl/evaluator repl :repl/namespace 'foo}
                  [:completions/var])
             => {:completions/var (m/embeds [{:text/contents "re-find"}
                                             {:text/contents "re-matches"}
                                             {:text/contents "re-pattern"}
                                             {:text/contents "re-seq"}])}))))

(deftest autocompletes-core-keywords
  (when (= helpers/*kind* :shadow)
    (async-test "autocompletion of keywords"
      (testing "gets keywords from the core namespace"
        (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
                eql (core/gen-eql)]
          (check (eql {:text/contents ":gen" :repl/evaluator repl :repl/namespace 'foo}
                      [:completions/keyword])
                 => {:completions/keyword (m/embeds [{:text/contents ":gen-class"}])}))))))

; (p/let [r (helpers/prepare-repl helpers/*global-evaluator*)]
;   (def repl r))
;
;
; (def ^:private valid-prefix #"/?([a-zA-Z0-9\-.$!?\/><*=\?_]+)")
;
; (defn- normalize-results [result]
;   (vec (some->> result
;                 helpers/parse-result
;                 :result
;                 (map (fn [c] {:type :function :candidate c})))))

; (def ^:private re-char-escapes
;   (->> "\\.*+|?()[]{}$^"
;        set
;        (map (juxt identity #(str "\\" %)))
;        (into {})))

; (defn- re-escape [prefix]
;   (str/escape (str prefix) re-char-escapes))

; (keys
;  (:result
;   (repl/eval repl "(in-ns 'foo) (ns-aliases *ns*)")))
;
; (let [prefix (->> txt-prefix (re-seq valid-prefix) last last)
;       cmd (str "(clojure.core/let [collect #(clojure.core/map "
;                                              "(clojure.core/comp str first) "
;                                              "(%1 %2)) "
;                                    "refers (collect clojure.core/ns-map *ns*)"
;                                    "from-ns (->> (clojure.core/ns-aliases *ns*) "
;                                              "(clojure.core/mapcat (fn [[k v]] "
;                                                "(clojure.core/map #(str k \"/\" %) "
;                                                "(collect clojure.core/ns-publics v)))))] "
;                 "(clojure.core/->> refers "
;                                   "(concat from-ns) "
;                                   "(clojure.core/filter #(re-find #\""
;                                                           'txt-prefix "\" %)) "
;                                   "(clojure.core/sort)"
;                                   "vec"
;                 "))")]
;   (if (not-empty prefix)
;     (.. (eval/eval repl cmd {:namespace ns-name :ignore true})
;         (then normalize-results)
;         (catch (constantly [])))
;     (p/promise [])))
;
