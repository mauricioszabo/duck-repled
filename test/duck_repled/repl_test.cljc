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
        (when-let [ns (:namespace options)]
          (sci/eval-string (str "(in-ns '" ns ")") {:env env}))
        (try
          {:result (sci/eval-string (str "(do " command "\n)") {:env env})}
          (catch :default e
            {:error e}))))))

(deftest eval-commands
  (async-test "given that you have a REPL, you can eval commands"
    (testing "evaluates command"
      (let [sci (prepare-sci)]
        (check (core/eql {:repl/evaluator sci :repl/code "(+ 1 2)" :repl/namespace 'foo}
                         [:repl/result])
               => {:repl/result {:result 3}})

        (check (core/eql {:repl/evaluator sci :repl/code "(ex-info)" :repl/namespace 'foo}
                         [:repl/error])
               => {:repl/error {:error any?}})))))

#_
(promesa.core/let
  [sci (prepare-sci)
   res (core/eql [:repl/result])]
  res)
  ; (meta res))

(defn- ^:dev/after-load run []
  (run-tests))
