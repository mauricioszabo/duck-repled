(ns duck-repled.editor-test
  (:require [check.async :refer [check testing async-test]]
            [clojure.test :refer [deftest run-tests]]
            [duck-repled.core :as core]))

(deftest editor-data
  (async-test "separates editor data into fragments"
    (check (core/eql {:editor/data {:contents "(+ 1 2)"
                                    :filename nil
                                    :range [[0 0] [0 0]]}}
                     [:editor/contents :editor/filename :editor/range])
           => {:editor/contents "(+ 1 2)"
               :editor/range [[0 0] [0 0]]})

    (check (core/eql {:editor/data {:contents "(+ 1 2)"
                                    :filename "foo.clj"
                                    :range [[0 0] [0 0]]}}
                     [:editor/contents :editor/filename :editor/range])
           => {:editor/contents "(+ 1 2)"
               :editor/filename "foo.clj"
               :editor/range [[0 0] [0 0]]})

    (testing "gets current var"
      (check (core/eql {:editor/data {:contents "foo bar"
                                      :filename "foo.clj"
                                      :range [[0 4] [0 4]]}}
                       [:editor/current-var :editor/current-var-range])
             => {:editor/current-var-range [[0 4] [0 6]]
                 :editor/current-var "bar"}))))

(deftest ns-from-contents
  (async-test "gets the current namespace from file"
    (let [seed {:editor/contents "\n(ns first.namespace)\n\n(+ 1 2)\n\n(ns second.ns)\n\n(+ 3 4)"}]
      (testing "gets namespace if declaration is below current selection"
        (check (core/eql (assoc seed :editor/range [[0 0] [0 0]])
                         [:editor/ns-range :editor/namespace])
               => {:editor/ns-range [[1 0] [1 19]]
                   :editor/namespace "first.namespace"}))

      (testing "gets namespace if declaration is above current selection"
        (check (core/eql (assoc seed :editor/range [[2 0] [2 0]])
                         [:editor/ns-range :editor/namespace])
               => {:editor/ns-range [[1 0] [1 19]]
                   :editor/namespace "first.namespace"})

        (check (core/eql (assoc seed :editor/range [[5 0] [5 0]])
                         [:editor/ns-range :editor/namespace])
               => {:editor/ns-range [[5 0] [5 13]]
                   :editor/namespace "second.ns"}))

      (testing "gets REPL namespace if a ns exists"
        (check (core/eql (assoc seed :editor/range [[2 0] [2 0]])
                         [:repl/namespace])
               => {:repl/namespace 'first.namespace}))

      (testing "fallback to default if there's no NS in editor"
        (check (core/eql {:editor/contents "" :editor/range [[2 0] [2 0]]
                          :cljs/required? false}
                         [:repl/namespace])
               => {:repl/namespace 'user})

        (check (core/eql {:editor/contents "" :editor/range [[2 0] [2 0]]
                          :cljs/required? true}
                         [:repl/namespace])
               => {:repl/namespace 'cljs.user})))))


(defn- ^:dev/after-load run []
  (run-tests))

#_
(core/eql {:editor/data {:contents "(+ 1 2)"
                                    :filename "foo.clj"
                                    :range [[0 0] [0 0]]}}
          [:editor/contents :editor/filename :editor/range])
