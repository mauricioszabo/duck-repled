(ns duck-repled.editor-test
  (:require [check.async :refer [check testing async-test]]
            [clojure.test :refer [deftest run-tests]]
            [duck-repled.core :as core]))

(deftest editor-data
  (async-test "separates editor data into fragments"
    (check (core/eql {:editor/data {:contents "(+ 1 2)"
                                    :filename "foo.clj"
                                    :range [[0 0] [0 0]]}}
                     [:editor/contents :editor/filename :editor/range])
           => {:editor/contents "(+ 1 2)"
               :editor/filename "foo.clj"
               :editor/range [[0 0] [0 0]]})))

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
                   :editor/namespace "second.namespace"})))))

(defn- ^:dev/after-load run []
  (run-tests))

#_
(core/eql {:editor/data {:contents "(+ 1 2)"
                                    :filename "foo.clj"
                                    :range [[0 0] [0 0]]}}
          [:editor/contents :editor/filename :editor/range])
