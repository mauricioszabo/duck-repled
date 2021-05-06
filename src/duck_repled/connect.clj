(ns duck-repled.connect
  (:require [promesa.core :as p]
            [com.wsscode.pathom3.connect.operation :as pco]
            [duck-repled.schemas :as schemas]))

(defmacro defresolver [name & args]
  `(do
     (pco/defresolver ~name ~@args)
     (let [original# ~name
           resolver# (:resolve original#)]
       (set! ~name (assoc original# :resolve
                          (fn [a# b#]
                            (p/let [result# (resolver# a# b#)]
                              (schemas/validate! (keys result#) result#))))))))
