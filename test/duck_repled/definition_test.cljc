(ns duck-repled.definition-test
  (:require [check.async :refer [check testing async-test]]
            [clojure.test :refer [deftest run-tests]]
            [duck-repled.core :as core]
            [promesa.core :as p]
            [duck-repled.repl-helpers :as helpers]
            [duck-repled.repl-protocol :as repl]))

(def eql (core/gen-eql))
; (deftest file-checkers
;   (async-test "finds if file exists"
;     (check (eql {:file/path ["test" "duck_repled" "tests.cljs"]}
;                 [:file/filename :file/exists?])
;            => {:file/filename "test/duck_repled/tests.cljs"
;                :file/exists? true})))

; (deftest simple-definition-finding
;   (async-test "find definition row, and filename if exist"
;     (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
;             seed {:repl/evaluators {:clj repl}
;                   :editor/data {:contents "(ns foo)\nmy-fun"
;                                 :filename "file.clj"
;                                 :range [[1 0] [1 0]]}
;                   :config/eval-as :prefer-clj}]
;
;       (when (#{:sci} helpers/*kind*)
;         (p/do!
;          (check (eql seed [:definition/row]) => {:definition/row 1})
;          (check (eql seed [:definition/filename])
;                 => {:definition/filename "test/duck_repled/tests.cljs"}))))))

; (deftest ns-definition
;   (when (#{:sci} helpers/*kind*)
;     (async-test "find definition of a namespace"
;       (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
;               seed {:repl/evaluators {:clj repl}
;                     :editor/data {:contents "(ns foo)\nmy-fun"
;                                   :filename "file.clj"
;                                   :range [[0 5] [0 5]]}
;                     :config/eval-as :prefer-clj}]
;
;          (check (eql seed [:definition/filename :definition/row :definition/col])
;                 => {:definition/filename "test/duck_repled/tests.cljs"
;                     :definition/row 0
;                     :definition/col 4})))))

(deftest resolving-filenames-in-clj
  (async-test "resolves filenames and contents, if inside jar"
    (when (= :shadow helpers/*kind*)
      (p/let [[clj cljs] (helpers/prepare-two-repls)
              seed {:repl/evaluators {:cljs cljs :clj clj}
                    :editor/data {:contents "(ns foo)\nstr/replace"
                                  :range [[1 0] [1 0]]}
                    :config/eval-as :prefer-clj}]
        (def seed seed)
        (p/do!
         (testing "finds JAR and unpacks in CLJ and CLJS funcions"
           (check (eql (assoc-in seed [:editor/data :filename] "file.clj")
                       [:definition/filename :definition/file-contents])
                  => {:definition/filename #"clojure.*jar!/clojure/string.clj"
                      :definition/file-contents string?})

           (check (eql (assoc-in seed [:editor/data :filename] "file.cljs")
                       [:definition/filename :definition/file-contents])
                  => {:definition/filename #"clojure.*jar!/clojure/string.cljs"
                      :definition/file-contents string?}))

         (testing "getting path of stacktrace"
           (check (eql (-> seed
                           (assoc-in [:editor/data :filename] "file.clj")
                           (assoc-in [:editor/data :contents] "str\nstr")
                           (assoc :ex/function-name "clojure.string/fn/eval1234"
                                  :ex/filename "string.clj"
                                  :ex/row 9
                                  :repl/evaluator (-> seed :repl/evaluators :clj)))
                       [:definition/filename :definition/row])
                  => {:definition/row 8
                      :definition/filename #"clojure.*jar!/clojure/string.clj"})))))))

; (deftest resolving-filenames-in-cljr
;   (async-test "resolves filenames and contents, if internal from CLR"
;     (when (= :cljr helpers/*kind*)
;       (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
;               seed {:repl/evaluators {:clj repl}
;                     :repl/kind :cljr
;                     :editor/data {:contents "(ns foo)\nstr/replace"
;                                   :filename "file.clj"
;                                   :range [[1 0] [1 0]]}
;                     :config/eval-as :prefer-clj}]
;         (check (eql seed [:definition/filename :definition/row])
;                => {:definition/filename #"clojure.main.*string.clj"
;                    :definition/row number?})))))

#?(:cljs
   (defn- ^:dev/after-load run []
     (run-tests)))
