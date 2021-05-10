(ns duck-repled.repl-test
  (:require [check.async :refer [check testing async-test]]
            [clojure.test :refer [deftest run-tests]]
            [duck-repled.core :as core]
            [sci.core :as sci]
            [duck-repled.repl-protocol :as repl]))

(defn- prepare-sci []
  (let [env (atom {})]
    (sci/eval-string (str "(ns foo (:require [clojure.string :as str]))\n"
                          "(def some-var 10)\n")
                     {:env env})
    (reify repl/Evaluator
      (-evaluate [_ command options]
        (let [cmd (if-let [ns (:namespace options)]
                    (str "(do (in-ns '" ns ") "command "\n)")
                    (str "(do " command "\n)"))]
          (try
            {:result (sci/eval-string cmd {:env env})
             :options options}
            (catch :default e
              {:error e})))))))

(deftest eval-commands
  (async-test "given that you have a REPL, you can eval commands"
    (let [sci (prepare-sci)]
      (testing "evaluates command"
        (check (core/eql {:repl/evaluator sci :text/contents "(+ 1 2)"}
                         [:repl/result])
               => {:repl/result {:result 3}})

        (check (core/eql {:repl/evaluator sci :text/contents "(ex-info)"}
                         [:repl/error])
               => {:repl/error {:error any?}}))

      (testing "sends ROW/COL and NS if available"
        (check (core/eql {:repl/evaluator sci :repl/namespace 'foo
                          :text/contents "some-var" :text/range [[2 4] [4 5]]}
                         [:repl/result])
               => {:repl/result {:result 10 :options {:row 2 :col 4}}}))

      (testing "evaluates fragments of editor in REPL"
        (promesa.core/let [sci (prepare-sci)
                           editor {:filename "foo.clj"
                                   :contents "(ns foo)\n(+ 1 2)\n(-  (+ 3 4)\n    (+ 5 some-var))"
                                   :range [[3 7] [3 8]]}]
          (check (core/eql {:editor/data editor :repl/evaluator sci}
                           [{:editor/selection [ :repl/result]}
                            {:editor/block [ :repl/result]}
                            {:editor/top-block [ :repl/result]}])
                 => {:editor/selection {:repl/result {:result 5}}
                     :editor/block {:repl/result {:result 15}}
                     :editor/top-block {:repl/result {:result -8}}}))))))

#_
(deftest repl-definition
  (async-test "will run on CLJ or CLJS REPL depending on what's expected"
    (p/let [clj-ish (prepare-sci)
            cljs-ish (prepare-sci)]
      (repl/eval clj-ish "(def flavor :clj)" {:namespace "foo"})
      (repl/eval cljs-ish "(def flavor :cljs)" {:namespace "foo"})

      (testing "will check for "))))

(defn- ^:dev/after-load run []
  (run-tests))
