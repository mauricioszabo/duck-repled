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

(deftest autocompletes-vars
  (async-test "autocompletion of vars"
    (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
            eql (core/gen-eql)]
      (check (eql {:text/contents "re-" :repl/evaluator repl :repl/namespace 'foo}
                  [:completions/var])
             => {:completions/var (m/embeds [{:text/contents "re-find"}
                                             {:text/contents "re-matches"}
                                             {:text/contents "re-pattern"}
                                             {:text/contents "re-seq"}])})

      (check (eql {:text/contents "str/re" :repl/evaluator repl :repl/namespace 'foo}
                  [:completions/var])
             => {:completions/var (m/embeds [{:text/contents "str/replace"}
                                             {:text/contents "str/replace-first"}])}))))

(deftest autocompletes-keywords
  (when (= helpers/*kind* :shadow)
    (async-test "autocompletion of keywords"
      (testing "gets keywords from the core namespace"
        (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
                eql (core/gen-eql)]
          (check (eql {:text/contents ":gen" :repl/evaluator repl :repl/namespace 'foo}
                      [:completions/keyword])
                 => {:completions/keyword (m/embeds [{:text/contents ":gen-class"}])})))

      (testing "gets keywords from other namespaces"
        (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
                eql (core/gen-eql)]
          (check (eql {:text/contents ":clojure.error/k" :repl/evaluator repl :repl/namespace 'foo}
                      [:completions/keyword])
                 => {:completions/keyword (m/embeds [{:text/contents ":clojure.error/keys"}])})))

      (testing "resolves keywords from other namespaces"
        (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
                eql (core/gen-eql)]
          (check (eql {:text/contents "::m/me" :repl/evaluator repl :repl/namespace 'foo}
                      [:completions/keyword])
                 => {:completions/keyword (m/embeds [{:text/contents "::m/message"}])}))))))
