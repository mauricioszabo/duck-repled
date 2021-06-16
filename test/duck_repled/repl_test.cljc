(ns duck-repled.repl-test
  (:require [check.async :refer [check testing async-test]]
            [clojure.test :refer [deftest]]
            [duck-repled.core :as core]
            [promesa.core :as p]
            [duck-repled.repl-helpers :as helpers]
            [duck-repled.repl-protocol :as repl]))

(def eql (core/gen-eql))
(deftest repl-definition
  (async-test "will run on CLJ or CLJS REPL depending on what's expected" {:timeout 8000}
    (p/let [[clj-ish cljs-ish] (helpers/prepare-two-repls)
            seed {:repl/evaluators {:cljs cljs-ish :clj clj-ish}
                  :editor/data {:contents "(ns foo)\nflavor"
                                :range [[1 0] [1 0]]}
                  :config/eval-as :prefer-clj}]

      (p/do!
       (repl/eval clj-ish "(def flavor :clj)" {:namespace "foo"})
       (when cljs-ish
         (repl/eval cljs-ish "(def flavor :cljs)" {:namespace "foo"}))

       (testing "will use Clojure REPL"
         (check (eql (assoc-in seed [:editor/data :filename] "file.clj")
                     [{:text/current-var [:repl/result]}])
                => {:text/current-var {:repl/result {:result :clj}}}))

       (when cljs-ish
         (testing "will use ClojureScript REPL"
           (check (eql (assoc-in seed [:editor/data :filename] "file.cljs")
                       [{:text/current-var [:repl/result]}])
                  => {:text/current-var {:repl/result {:result :cljs}}})))))))

(deftest eval-commands
  (async-test "will run on CLJ or CLJS REPL depending on what's expected" {:timeout 8000}
    (p/let [sci (helpers/prepare-repl helpers/*global-evaluator*)]
      (p/do!
       (testing "evaluates command"
         (check (eql {:repl/evaluator sci
                      :text/contents "(+ 1 2)"}
                     [:repl/result])
                => {:repl/result {:result 3}})

         (check (eql {:repl/evaluator sci
                      :text/contents "(ex-info)"}
                     [:repl/error])
                => {:repl/error {:error any?}}))

       (testing "sends ROW/COL and NS if available"
         (check (eql {:repl/evaluator sci :repl/namespace 'foo
                      :editor/filename "file.clj"
                      :text/contents "some-var" :text/range [[2 4] [4 5]]}
                     [:repl/result])
                => {:repl/result {:result 10 :options {:row 2
                                                       :col 4
                                                       :filename "file.clj"}}}))

       (testing "applies template/options to code and evaluate"
         (check (eql {:repl/evaluator sci :text/contents "(+ 1 2)"}
                     ['(:repl/result {:repl/template (inc :repl/code)
                                      :row 20})])
                => {:repl/result {:result 4
                                  :options {:row 20}}}))

       (testing "evaluates fragments of editor in REPL"
         (p/let [editor {:filename "foo.clj"
                         :contents "(ns foo)\n(+ 1 2)\n(-  (+ 3 4)\n    (+ 5 some-var))"
                         :range [[3 7] [3 8]]}]
           (check (eql {:editor/data editor :repl/evaluator sci}
                       [{:text/selection [:repl/result]}
                        {:text/block [:repl/result]}
                        {:text/top-block [:repl/result]}])
                  => {:text/selection {:repl/result {:result 5}}
                      :text/block {:repl/result {:result 15}}
                      :text/top-block {:repl/result {:result -8}}})))))))

(deftest getting-infos-about-vars
  (async-test "will get full-qualified name of a var" {:timeout 8000}
    (p/let [evaluator (helpers/prepare-repl helpers/*global-evaluator*)
            seed {:repl/evaluators {:clj evaluator}
                  :editor/data {:contents "(ns foo)\nmy-fun"
                                :filename "file.clj"
                                :range [[1 0] [1 0]]}
                  :config/eval-as :prefer-clj}]
      (p/do!
       (testing "will find fqn for current var"
         (check (eql seed [:var/fqn]) => {:var/fqn 'foo/my-fun}))

       (testing "will get full qualified name of selection"
         (check (eql seed [{:text/selection [:var/fqn]}])
                => {:text/selection {:var/fqn 'foo/m}}))))))

(deftest getting-meta-and-dependent
  (when helpers/*cljs-evaluator*
    (async-test "will run on CLJ or CLJS REPL depending on what's expected" {:timeout 8000}
      (p/let [[evaluator cljs] (helpers/prepare-two-repls)
              seed {:repl/evaluators {:clj evaluator :cljs cljs}
                    :editor/data {:contents "(ns foo)\nmy-fun"
                                  :filename "file.clj"
                                  :range [[1 0] [1 0]]}
                    :config/eval-as :prefer-clj}]
        (p/do!
         (repl/eval evaluator "(defn my-clj-fun \"Another doc\" [] (+ 1 2))\n" {:namespace "foo"})
         (testing "will get metadata of a var"
           (check (eql seed [:var/meta]) => {:var/meta {:doc "My doc"}}))

         (testing "will get metadata of a var using CLJS"
           (check (eql (-> seed
                           (assoc :repl/kind :cljs :config/repl-kind :clj))
                       [:var/meta])
                  => {:var/meta {:doc "My doc"}}))

         (testing "will get metadata of a var using Clojure if it doesn't exist in CLJS"
           (check (eql (-> seed
                           (assoc :repl/kind :cljs :config/repl-kind :clj)
                           (assoc-in [:editor/data :contents] "(ns foo)\nmy-clj-fun"))
                       [:var/meta])
                  => {:var/meta {:doc "Another doc"}}))

         (testing "will get DOC for that current var"
           (check (eql (-> seed
                           (assoc :repl/kind :cljs :config/repl-kind :clj)
                           (assoc-in [:editor/data :contents] "(ns foo)\nmy-clj-fun"))
                       [:var/doc])
                  => {:var/doc (str "-------------------------\n"
                                    "foo/my-clj-fun\n"
                                    "([])\n"
                                    "  Another doc")}))))))

  (when (#{:clje} helpers/*kind*)
    (async-test "will coerce infos from #erl maps" {:timeout 8000}
      (p/let [evaluator (helpers/prepare-repl helpers/*global-evaluator*)
              seed {:repl/evaluators {:clj evaluator}
                    :editor/data {:contents "(ns foo)\nmy-fun"
                                  :filename "file.clj"
                                  :range [[1 0] [1 0]]}
                    :config/repl-kind :clje
                    :config/eval-as :prefer-clj}]
        (p/do!
         (testing "will get metadata of a var"
           (check (eql seed [:var/meta]) => {:var/meta {:doc "My doc"}}))

         (testing "will get DOC for that current var"
           (check (eql seed [:var/doc])
                  => {:var/doc (str "-------------------------\n"
                                    "foo/my-fun\n"
                                    "([])\n"
                                    "  My doc")})))))))
