(ns duck-repled.core-test
  (:require [check.async :refer [check testing async-test]]
            [clojure.test :refer [deftest]]
            [duck-repled.core :as core]
            [promesa.core :as p]))

(deftest resolver-customization
  (async-test "custom resolvers"
    (testing "customizing resolves"
      (testing "will add a new resolver with our code"
        (let [eql (core/gen-eql
                   (core/add-resolver {:outputs [:editor/file] :inputs [:editor/text]}
                                      (fn [{:editor/keys [text]}]
                                        {:editor/file (str "filename for "
                                                           (:text/contents text))})))]
          (check (eql {:editor/data {:contents "lol"
                                     :filename ""
                                     :range [[0 0] [0 0]]}}
                      [:editor/file])
                 => {:editor/file "filename for lol"})))

      (testing "will compose original resolver, and add our customization code"
        (let [eql
              (core/gen-eql
               (core/compose-resolver {:outputs [:editor/filename] :inputs [:editor/text]}
                                      (fn [{:editor/keys [filename text]}]
                                        {:editor/filename (str (:text/contents text)
                                                               "-" filename)})))]
          (check (eql {:editor/data {:contents "lol"
                                     :filename "old.clj"
                                     :range [[0 0] [0 0]]}}
                      [:editor/filename])
                 => {:editor/filename "lol-old.clj"}))))))
