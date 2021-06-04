(ns duck-repled.editor-test
  (:require [check.async :refer [check testing async-test]]
            [clojure.test :refer [deftest]]
            [duck-repled.core :as core]
            [promesa.core :as p]))

(def eql (core/gen-eql))
(deftest editor-data
  (async-test "separates editor data into fragments" {:timeout 8000}
    (check (eql {:editor/data {:contents "(+ 1 2)"
                               :filename nil
                               :range [[0 0] [0 0]]}}
                [:editor/contents :editor/filename :editor/range
                 :editor/text])
           => {:editor/text {:text/contents "(+ 1 2)"
                             :text/range [[0 0] [0 0]]}
               :editor/contents "(+ 1 2)"
               :editor/range [[0 0] [0 0]]})

    (check (eql {:editor/data {:contents "(+ 1 2)"
                               :filename "foo.clj"
                               :range [[0 0] [0 0]]}}
                [:editor/contents :editor/filename :editor/range])
           => {:editor/contents "(+ 1 2)"
               :editor/filename "foo.clj"
               :editor/range [[0 0] [0 0]]})

    ; (testing "gets current var"
    ;   (check (eql {:editor/data {:contents "foo bar"
    ;                              :filename "foo.clj"
    ;                              :range [[0 4] [0 4]]}}
    ;               [{:editor/current-var [:text/contents :text/range]}])
    ;          => {:editor/current-var {:text/range [[0 4] [0 6]]
    ;                                   :text/contents "bar"}}))
    ;
    ; (testing "gets current top-block"
    ;   (check (eql {:editor/data {:contents "(+ 1 2)\n\n( \n ( 2 3))"
    ;                              :filename "foo.clj"
    ;                              :range [[3 4] [3 7]]}}
    ;               [{:editor/top-block [:text/contents :text/range]}])
    ;          => {:editor/top-block {:text/range [[2 0] [3 7]]
    ;                                 :text/contents "( \n ( 2 3))"}}))
    ;
    ;
    ; (testing "gets current block"
    ;   (check (eql {:editor/data {:contents "(+ 1 2)\n\n( \n ( 2 3))"
    ;                              :filename "foo.clj"
    ;                              :range [[3 4] [3 7]]}}
    ;               [{:editor/block [:text/contents :text/range]}])
    ;          => {:editor/block {:text/range [[3 1] [3 6]]
    ;                             :text/contents "( 2 3)"}}))
    ;
    ; (testing "gets current selection"
    ;   (check (eql {:editor/data {:contents "(+ 1 2)\n\n( \n ( 2 3))"
    ;                              :filename "foo.clj"
    ;                              :range [[2 0] [3 6]]}}
    ;               [{:editor/selection [:text/contents :text/range]}])
    ;          => {:editor/selection {:text/range [[2 0] [3 6]]
    ;                                 :text/contents "( \n ( 2 3)"}}))

    (testing "gets block/selection/etc from `:text/*` elements"
      (check (eql {:text/contents "(ns lol)(+ 1 2)\n\n( \n ( 2 3))"
                   :text/range [[3 3] [3 7]]}
                  [{:text/block [:text/contents :text/range]}
                   {:text/ns [:text/contents :text/range]}
                   {:text/top-block [:text/contents :text/range]}
                   {:text/current-var [:text/contents :text/range]}
                   {:text/selection [:text/contents :text/range]}])
             => {:text/block {:text/range [[3 1] [3 6]]
                              :text/contents "( 2 3)"}
                 :text/top-block {:text/contents "( \n ( 2 3))"
                                  :text/range [[2 0] [3 7]]}
                 :text/current-var {:text/range [[3 3] [3 3]]
                                    :text/contents "2"}
                 :text/selection {:text/range [[3 3] [3 7]]
                                  :text/contents "2 3))"}
                 :text/ns {:text/contents "lol"
                           :text/range [[0 0] [0 7]]}}))

    (testing "gets block/selection/etc from text editor"
      (check (eql {:editor/data {:contents "(ns lol)(+ 1 2)\n\n( \n ( 2 3))"
                                 :filename nil
                                 :range [[3 3] [3 7]]}}
                  [{:text/block [:text/contents :text/range]}
                   {:text/ns [:text/contents :text/range]}
                   {:text/top-block [:text/contents :text/range]}
                   {:text/current-var [:text/contents :text/range]}
                   {:text/selection [:text/contents :text/range]}])
             => {:text/block {:text/range [[3 1] [3 6]]
                              :text/contents "( 2 3)"}
                 :text/top-block {:text/contents "( \n ( 2 3))"
                                  :text/range [[2 0] [3 7]]}
                 :text/current-var {:text/range [[3 3] [3 3]]
                                    :text/contents "2"}
                 :text/selection {:text/range [[3 3] [3 7]]
                                  :text/contents "2 3))"}
                 :text/ns {:text/contents "lol"
                           :text/range [[0 0] [0 7]]}}))

    (testing "keep reference of the current namespace for each element"
      (check (eql {:editor/data {:contents "(ns lol)(+ 1 2)\n\n( \n ( 2 3))"
                                 :filename nil
                                 :range [[3 3] [3 7]]}}
                  [{:text/block [:text/ns]}
                   {:text/top-block [:text/ns]}
                   {:text/current-var [:text/ns]}
                   {:text/selection [:text/ns]}])
             => {:text/block {:text/ns {:text/contents "lol"
                                        :text/range [[0 0] [0 7]]}}
                 :text/top-block {:text/ns {:text/contents "lol"
                                              :text/range [[0 0] [0 7]]}}
                 :text/current-var {:text/ns {:text/contents "lol"
                                              :text/range [[0 0] [0 7]]}}
                 :text/selection {:text/ns {:text/contents "lol"
                                            :text/range [[0 0] [0 7]]}}}))))

(deftest config-for-repl
  (async-test "separates editor data into fragments" {:timeout 8000}
    (testing "configuring everything to be CLJ or CLJS"
      (check (eql {:config/repl-kind :clj, :config/eval-as :clj}
                  [:repl/kind])
             => {:repl/kind :clj})

      (check (eql {:config/repl-kind :clj, :config/eval-as :cljs} [:repl/kind])
             => {:repl/kind :cljs}))

    (testing "will always be 'another kind' if we're not in CLJ REPL"
      (check (eql {:config/repl-kind :cljs, :config/eval-as :clj}
                  [:config/repl-kind :repl/kind])
             => {:repl/kind :cljs}))

    (testing "if `:prefer-clj` is used, will use clj on .clj and .cljc files"
      (check (eql {:config/repl-kind :clj
                   :config/eval-as :prefer-clj
                   :editor/filename "somefile.clj"}
                  [:repl/kind])
             => {:repl/kind :clj})

      (check (eql {:config/repl-kind :clj
                   :config/eval-as :prefer-clj
                   :editor/filename "somefile.cljc"}
                  [:repl/kind])
             => {:repl/kind :clj})

      (check (eql {:config/repl-kind :clj
                   :config/eval-as :prefer-clj
                   :editor/filename "somefile.cljs"}
                  [:repl/kind])
             => {:repl/kind :cljs}))

    (testing "if `:prefer-cljs` is used, will use cljs on .cljs and .cljc files"
      (check (eql {:config/repl-kind :clj
                   :config/eval-as :prefer-cljs
                   :editor/filename "somefile.clj"}
                  [:repl/kind])
             => {:repl/kind :clj})

      (check (eql {:config/repl-kind :clj
                   :config/eval-as :prefer-cljs
                   :editor/filename "somefile.cljc"}
                  [:repl/kind])
             => {:repl/kind :cljs})

      (check (eql {:config/repl-kind :clj
                   :config/eval-as :prefer-cljs
                   :editor/filename "somefile.cljs"}
                  [:repl/kind])
             => {:repl/kind :cljs}))))

(deftest ns-from-contents
  (async-test "separates editor data into fragments" {:timeout 8000}
    (let [seed {:editor/text
                {:text/contents
                 "\n(ns first.namespace)\n\n(+ 1 2)\n\n(ns second.ns)\n\n(+ 3 4)"}}]
      (p/do!)
      (testing "gets namespace if declaration is below current selection"
        (check (eql (assoc-in seed [:editor/text :text/range] [[0 0] [0 0]])
                    [:text/ns])
               => {:text/ns {:text/range [[1 0] [1 19]]
                                       :text/contents "first.namespace"}}))

      (testing "gets namespace if declaration is above current selection"
        (check (eql (assoc-in seed [:editor/text :text/range] [[2 0] [2 0]])
                    [:text/ns])
               => {:text/ns {:text/range [[1 0] [1 19]]
                             :text/contents "first.namespace"}})

        (check (eql (assoc-in seed [:editor/text :text/range]  [[5 0] [5 0]])
                    [:text/ns])
               => {:text/ns {:text/range [[5 0] [5 13]]
                             :text/contents "second.ns"}}))

      (testing "gets REPL namespace if a ns exists"
        (check (eql (assoc-in seed [:editor/text :text/range]  [[2 0] [2 0]])
                    [:repl/namespace])
               => {:repl/namespace 'first.namespace}))

      (testing "fallback to default if there's no NS in editor"
        (check (eql {:editor/contents "" :editor/range [[2 0] [2 0]]
                     :repl/kind :clj}
                    [:repl/namespace])
               => {:repl/namespace 'user})

        (check (eql {:editor/contents "" :editor/range [[2 0] [2 0]]
                     :repl/kind :cljs}
                    [:repl/namespace])
               => {:repl/namespace 'cljs.user})))))

(deftest read-file-contents
  (async-test "read file contents and extract fragments"
    (check (eql {:file/filename "test/duck_repled/tests.cljs"}
                [{:file/contents [:text/ns]}])
           => {:file/contents {:text/ns {:text/contents "duck-repled.tests"}}})

    (check (eql {:file/filename "test/duck_repled/tests.cljs"}
                [{'(:file/contents {:range [[10 0] [10 0]]}) [:text/contents :text/top-block]}])
           => {:file/contents {:text/top-block
                               {:text/contents #"^.defmethod test/report"
                                :text/range [[10 0] [11 48]]}}})))
