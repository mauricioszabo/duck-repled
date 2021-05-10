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
                       [{:editor/current-var [:text/contents :text/range]}])
             => {:editor/current-var {:text/range [[0 4] [0 6]]
                                      :text/contents "bar"}}))

    (testing "gets current top-block"
      (check (core/eql {:editor/data {:contents "(+ 1 2)\n\n( \n ( 2 3))"
                                      :filename "foo.clj"
                                      :range [[3 4] [3 7]]}}
                       [{:editor/top-block [:text/contents :text/range]}])
             => {:editor/top-block {:text/range [[2 0] [3 7]]
                                    :text/contents "( \n ( 2 3))"}}))


    (testing "gets current block"
      (check (core/eql {:editor/data {:contents "(+ 1 2)\n\n( \n ( 2 3))"
                                      :filename "foo.clj"
                                      :range [[3 4] [3 7]]}}
                       [{:editor/block [:text/contents :text/range]}])
             => {:editor/block {:text/range [[3 1] [3 6]]
                                :text/contents "( 2 3)"}}))

    (testing "gets current selection"
      (check (core/eql {:editor/data {:contents "(+ 1 2)\n\n( \n ( 2 3))"
                                      :filename "foo.clj"
                                      :range [[2 0] [3 6]]}}
                       [{:editor/selection [:text/contents :text/range]}])
             => {:editor/selection {:text/range [[2 0] [3 6]]
                                    :text/contents "( \n ( 2 3)"}}))))

(deftest config-for-repl
  (async-test "configure how we eval CLJ or CLJS"
    (testing "configuring everything to be CLJ or CLJS"
      (check (core/eql {:config/repl-kind :clj, :config/eval-as :clj}
                       [:repl/kind])
             => {:repl/kind :clj})

      (check (core/eql {:config/repl-kind :clj, :config/eval-as :cljs} [:repl/kind])
             => {:repl/kind :cljs}))

    (testing "will always be 'another kind' if we're not in CLJ REPL"
      (check (core/eql {:config/repl-kind :cljs, :config/eval-as :clj} [:repl/kind])
             => {:repl/kind :cljs}))

    (testing "if `:prefer-clj` is used, will use clj on .clj and .cljc files"
      (check (core/eql {:config/repl-kind :clj
                        :config/eval-as :prefer-clj
                        :editor/filename "somefile.clj"}
                       [:repl/kind])
             => {:repl/kind :clj})

      (check (core/eql {:config/repl-kind :clj
                        :config/eval-as :prefer-clj
                        :editor/filename "somefile.cljc"}
                       [:repl/kind])
             => {:repl/kind :clj})

      (check (core/eql {:config/repl-kind :clj
                        :config/eval-as :prefer-clj
                        :editor/filename "somefile.cljs"}
                       [:repl/kind])
             => {:repl/kind :cljs}))

    (testing "if `:prefer-cljs` is used, will use cljs on .cljs and .cljc files"
      (check (core/eql {:config/repl-kind :clj
                        :config/eval-as :prefer-cljs
                        :editor/filename "somefile.clj"}
                       [:repl/kind])
             => {:repl/kind :clj})

      (check (core/eql {:config/repl-kind :clj
                        :config/eval-as :prefer-cljs
                        :editor/filename "somefile.cljc"}
                       [:repl/kind])
             => {:repl/kind :cljs})

      (check (core/eql {:config/repl-kind :clj
                        :config/eval-as :prefer-cljs
                        :editor/filename "somefile.cljs"}
                       [:repl/kind])
             => {:repl/kind :cljs}))))

(deftest ns-from-contents
  (async-test "gets the current namespace from file"
    (let [seed {:editor/contents "\n(ns first.namespace)\n\n(+ 1 2)\n\n(ns second.ns)\n\n(+ 3 4)"}]
      (testing "gets namespace if declaration is below current selection"
        (check (core/eql (assoc seed :editor/range [[0 0] [0 0]])
                         [{:editor/ns [:text/range :text/contents]}])
               => {:editor/ns {:text/range [[1 0] [1 19]]
                               :text/contents "first.namespace"}}))

      (testing "gets namespace if declaration is above current selection"
        (check (core/eql (assoc seed :editor/range [[2 0] [2 0]])
                         [{:editor/ns [:text/range :text/contents]}])
               => {:editor/ns {:text/range [[1 0] [1 19]]
                               :text/contents "first.namespace"}})

        (check (core/eql (assoc seed :editor/range [[5 0] [5 0]])
                         [{:editor/ns [:text/range :text/contents]}])
               => {:editor/ns {:text/range [[5 0] [5 13]]
                               :text/contents "second.ns"}}))

      (testing "gets REPL namespace if a ns exists"
        (check (core/eql (assoc seed :editor/range [[2 0] [2 0]])
                         [:repl/namespace])
               => {:repl/namespace 'first.namespace}))

      (testing "fallback to default if there's no NS in editor"
        (check (core/eql {:editor/contents "" :editor/range [[2 0] [2 0]]
                          :repl/kind :clj}
                         [:repl/namespace])
               => {:repl/namespace 'user})

        (check (core/eql {:editor/contents "" :editor/range [[2 0] [2 0]]
                          :repl/kind :cljs}
                         [:repl/namespace])
               => {:repl/namespace 'cljs.user})))))

(defn- ^:dev/after-load run []
  (run-tests))
