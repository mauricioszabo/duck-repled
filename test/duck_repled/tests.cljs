(ns duck-repled.tests
  (:require [duck-repled.editor-test]
            [duck-repled.repl-test]
            [clojure.test :as test]
            [promesa.core :as p]
            [duck-repled.repl-helpers :as helpers]))

(defn main [ & args]
  (when (-> args first (= "--test"))
    (defmethod test/report [::test/default :summary] [{:keys [fail error] :as result}]
      (if (= 0 fail error)
        (js/process.exit 0)
        (js/process.exit 1))))

  (if (-> args count (= 2))
    (p/let [repl (helpers/connect-socket! "localhost"
                                          (-> args second js/parseInt))]
      (set! helpers/*global-evaluator* repl)
      (test/run-all-tests))
    (test/run-all-tests))

  (prn :loaded))
