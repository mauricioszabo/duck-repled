(ns duck-repled.template
  (:require [clojure.walk :as walk]))

(defn template [code replaces]
  (let [code (->> code
                  (walk/postwalk (fn [sym]
                                   (if (and (symbol? sym)
                                            (-> sym namespace (= "cljs.core")))
                                     (symbol "clojure.core" (name sym))
                                     sym))))]
    (pr-str (walk/postwalk-replace replaces code))))
