(ns duck-repled.definition-test
  (:require [check.async :refer [check testing async-test]]
            [clojure.test :refer [deftest run-tests]]
            [duck-repled.core :as core]
            [promesa.core :as p]
            [duck-repled.repl-helpers :as helpers]
            [duck-repled.repl-protocol :as repl]))

(deftest file-checkers
  (async-test "finds if file exists"
    (check (core/eql {:file/path ["test" "duck_repled" "tests.cljs"]}
                     [:file/filename :file/exists?])
           => {:file/filename "test/duck_repled/tests.cljs"
               :file/exists? true})))

(deftest simple-definition-finding
  (async-test "find definition row, and filename if exist"
    (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
            seed {:repl/evaluators {:clj repl}
                  :editor/data {:contents "(ns foo)\nmy-fun"
                                :filename "file.clj"
                                :range [[1 0] [1 0]]}
                  :config/eval-as :prefer-clj}]

      (when (#{:sci} helpers/*kind*)
        (p/do!
         (check (core/eql seed [:definition/row]) => {:definition/row 1})
         (check (core/eql seed [:definition/filename])
                => {:definition/filename "test/duck_repled/tests.cljs"}))))))

(deftest resolving-filenames-in-clj
  (async-test "resolves filenames and contents, if inside JAR"
    (when (= :shadow helpers/*kind*)
      (p/let [[clj cljs] (helpers/prepare-two-repls)
              seed {:repl/evaluators {:cljs cljs :clj clj}
                    :editor/data {:contents "(ns foo)\nstr/replace"
                                  :range [[1 0] [1 0]]}
                    :config/eval-as :prefer-clj}]
        (p/do!
         (testing "finds JAR and unpacks in CLJ and CLJS funcions"
           (check (core/eql (assoc-in seed [:editor/data :filename] "file.clj")
                            [:definition/filename :definition/file-contents])
                  => {:definition/filename #"clojure.*jar!/clojure/string.clj"
                      :definition/file-contents string?})

           (check (core/eql (assoc-in seed [:editor/data :filename] "file.cljs")
                            [:definition/filename :definition/file-contents])
                  => {:definition/filename #"clojure.*jar!/clojure/string.cljs"
                      :definition/file-contents string?})))))

    (when (= :cljr helpers/*kind*)
      (p/let [repl (helpers/prepare-repl helpers/*global-evaluator*)
              seed {:repl/evaluators {:clj repl}
                    :repl/kind :cljr
                    :editor/data {:contents "(ns foo)\nstr/replace"
                                  :filename "file.clj"
                                  :range [[1 0] [1 0]]}
                    :config/eval-as :prefer-clj}]
        (check (core/eql seed [:definition/filename :definition/row])
               => {:definition/filename #"clojure.main.*string.clj"
                   :definition/row number?})))))

#?(:cljs
   (defn- ^:dev/after-load run []
     (run-tests)))
