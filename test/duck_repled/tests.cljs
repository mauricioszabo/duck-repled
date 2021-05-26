(ns duck-repled.tests
  (:require [duck-repled.editor-test]
            [duck-repled.repl-test]
            [duck-repled.definition-test]
            [duck-repled.core-test]
            [clojure.test :as test]
            [promesa.core :as p]
            [duck-repled.repl-helpers :as helpers]
            [duck-repled.repl-protocol :as repl]))

(defmethod test/report [::test/default :begin-test-var] [m]
  (println "Testing:" (test/testing-vars-str m)))

(defn- connect-socket! [[port kind]]
  (set! helpers/*global-evaluator* #(helpers/connect-socket!
                                     "localhost"
                                     (js/parseInt port)))

  (set! helpers/*kind* (or (some-> kind keyword) :not-shadow))
  (if (= :shadow helpers/*kind*)
    (set! helpers/*cljs-evaluator* #(helpers/connect-node-repl!
                                     "localhost"
                                     (js/parseInt port)))
    (set! helpers/*cljs-evaluator* nil)))

(defn main [ & args]
  ; (when (->> args first (re-matches #"\d+"))
  ;   (p/let [repl (helpers/connect-node-repl! "localhost" (js/parseInt (first args)))]
  ;     (def repl repl)))

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
    (connect-socket! (rest args))
    (p/do!
     (if helpers/*cljs-evaluator*
       (p/let [res (p/race [(p/delay 120000 ::error)
                            (helpers/*cljs-evaluator*)])]
         (if (= res ::error)
           (do
             (println "CLJS REPL didn't connect in 2m")
             (js/process.exit 2))
           (test/run-all-tests #"duck-repled.*-test")))
       (test/run-all-tests #"duck-repled.*-test"))))

  (when (= [] args)
    (prn :loaded)))
