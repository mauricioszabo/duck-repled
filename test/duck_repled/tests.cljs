(ns duck-repled.tests
  (:require [duck-repled.editor-test]
            [duck-repled.repl-test]
            [clojure.test :as test]
            [promesa.core :as p]
            [duck-repled.repl-helpers :as helpers]
            [duck-repled.repl-protocol :as repl]))

(defmethod test/report [::test/default :begin-test-var] [m]
  (println "Testing:" (test/testing-vars-str m)))

(defn main [ & args]
  (when (-> args first (= "--test"))
    (defmethod test/report [::test/default :summary] [{:keys [test pass fail error]}]
      (println "Ran" test "tests containing" (+ pass fail error) "assertions.")
      (println pass "passed," fail "failures," error "errors.")
      (if (= 0 fail error)
        (js/process.exit 0)
        (js/process.exit 1)))
    (when (-> args count (= 1))
      (test/run-all-tests #"duck-repled.*-test")))

  (when (-> args count (>= 2))
    (set! helpers/*global-evaluator* #(helpers/connect-socket!
                                       "localhost"
                                       (-> args second js/parseInt)))

    (when (-> args count (>= 3))
      (prn :SET (nth args 2))
      (set! helpers/*kind* (keyword (nth args 2))))
    (test/run-all-tests #"duck-repled.*-test"))

  (when (= [] args)
    (prn :loaded)))
