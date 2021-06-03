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
                   (core/add-resolver {:outputs [:editor/file] :inputs [:editor/contents]}
                                      (fn [{:editor/keys [contents]}]
                                        {:editor/file (str "filename for " contents)})))]
          (check (eql {:editor/data {:contents "lol"
                                     :filename ""
                                     :range [[0 0] [0 0]]}}
                      [:editor/file])
                 => {:editor/file "filename for lol"})))

      (testing "will compose original resolver, and add our customization code"
        (let [eql
              (core/gen-eql
               (core/compose-resolver {:outputs [:editor/filename] :inputs [:editor/contents]}
                                      (fn [{:editor/keys [filename contents]}]
                                        {:editor/filename (str contents "-" filename)})))]
          (check (eql {:editor/data {:contents "lol"
                                     :filename "old.clj"
                                     :range [[0 0] [0 0]]}}
                      [:editor/filename])
                 => {:editor/filename "lol-old.clj"}))))))
