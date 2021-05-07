(ns duck-repled.tests
  (:require [duck-repled.editor-test]
            [duck-repled.repl-test]
            [clojure.test :as test]))

(defn main [ & args]
  (when (= args ["--test"])
    (defmethod test/report [::test/default :summary] [{:keys [fail error]}]
      (if (= 0 fail error)
        (js/process.exit 0)
        (js/process.exit 1)))
    (test/run-all-tests))
  (prn :loaded))
