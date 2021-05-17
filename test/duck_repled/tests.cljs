(ns duck-repled.tests
  (:require [duck-repled.editor-test]
            [duck-repled.repl-test]
            [duck-repled.definition-test]
            [clojure.test :as test]
            [promesa.core :as p]
            [duck-repled.repl-helpers :as helpers]))

(defn main [ & args]
  (when (-> args first (= "--test"))
    (defmethod test/report [::test/default :summary] [{:keys [fail error] :as result}]
      (if (= 0 fail error)
        (js/process.exit 0)
        (js/process.exit 1)))
    (when (-> args count (= 1))
      (test/run-all-tests)))

  (when (-> args count (>= 2))
    (p/let [repl (helpers/connect-socket! "localhost"
                                          (-> args second js/parseInt))]
      (set! helpers/*global-evaluator* repl)
      (set! helpers/*kind* (or (some->> args (drop 2) first keyword)
                               :not-shadow))
      (test/run-all-tests)))

  (when (= [] args)
    (prn :loaded)))
