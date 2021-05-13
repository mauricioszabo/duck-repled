(ns duck-repled.connect
  (:require [promesa.core :as p]
            [com.wsscode.pathom3.connect.operation :as pco]
            [duck-repled.schemas :as schemas]))

(defmacro defresolver [name & args]
  (let [is-cljs (:ns &env)
        original (gensym "original")
        resolver (gensym "resolver")
        outputs (gensym "outputs")
        op (gensym "op")]
    `(do
       (pco/defresolver ~name ~@args)
       (let [~original ~name
             ~resolver (:resolve ~original)
             ~outputs (-> ~original
                       :config
                       :com.wsscode.pathom3.connect.operation/output)
             ~op (-> ~original
                  :config
                  :com.wsscode.pathom3.connect.operation/op-name)]
         ~(if is-cljs
            `(set! ~name (assoc ~original :resolve
                                (fn [a# b#]
                                  (p/let [result# (~resolver a# b#)]
                                    (when result#
                                      (schemas/validate! (keys result#)
                                                         result#
                                                         (str "Invalid schema on "
                                                              ~op
                                                              " outputing "
                                                              ~outputs)))))))
            `(alter-var-root (var ~name)
                             assoc :resolve
                             (fn [a# b#]
                               (p/let [result# (~resolver a# b#)]
                                 (when result#
                                   (schemas/validate! (keys result#)
                                                      result#
                                                      (str "Invalid schema on "
                                                           ~op
                                                           " outputing "
                                                           ~outputs)))))))))))
