(ns duck-repled.repl-helpers
  (:require [duck-repled.repl-protocol :as repl]
            [promesa.core :as p]
            [clojure.edn :as edn]
            [sci.core :as sci]
            #?(:cljs ["net" :as net])))

(defn- treat-data [pending data]
  (let [full (:buffer (swap! pending update :buffer str data))
        [_ id result] (re-find #"(?s)__eval_result_beg (.*?) (.*?) __eval_result_end" full)]
    (when result
      (let [{:keys [promise opts]} (get @pending id)
            result (try
                      (edn/read-string {:default tagged-literal} result)
                      (catch #?(:cljs :default :clj Throwable) _
                        {:result result}))]
        (swap! pending dissoc :buffer id)
        (p/resolve! promise (merge result opts))))))

(defn- connect! [host port]
  #?(:cljs
     (js/Promise.
      (fn [resolve fail]
        (let [pending (atom {})
              conn (. net createConnection port host)]
          (.on conn "connect" #(resolve {:conn conn :pending pending}))
          (.on conn "data" #(treat-data pending %)))))))


(def ^:private ex
  (str
   "#?("
   " :clj java.lang.Throwable"
   " :joker Error"
   " :cljs :default"
   " :cljr System.Exception"
   " :clje _)"))

(defn- eval! [^js conn pending command options]
  (when-let [namespace (:namespace options)]
    (.write conn (str "(in-ns '" namespace ")\n")))
  (let [id (gensym "repl-eval-")
        promise (p/deferred)]
    (swap! pending assoc (str id) {:promise promise :opts options})
    (.write conn (str "['__eval_result_beg"
                      " '" id
                      " (try {:result " command
                      "\n} (catch " ex " t {:error t}))"
                      " '__eval_result_end]\n"))
    promise))

(defn connect-socket! [host port]
  (p/let [{:keys [pending conn]} (connect! host port)]
    (reify repl/Evaluator
      (-evaluate [_ command options]
        (eval! conn pending command options)))))

(defn connect-sci! []
  (let [env (atom {})]
    (reify repl/Evaluator
      (-evaluate [_ command options]
        (let [cmd (if-let [ns (:namespace options)]
                    (str "(in-ns '" ns ")\n" command "\n")
                    (str command "\n"))]
          (try
            {:result
             (sci/binding [sci/file (:filename options)]
                (sci/eval-string cmd {:env env}))
             :options options}
            (catch #?(:clj Throwable :cljs :default) e
              {:error e})))))))

(defn prepare-repl [evaluator]
  (when evaluator
    (doto evaluator
          (repl/eval (str "(ns foo (:require [clojure.string :as str]))\n"
                          "(defn my-fun \"My doc\" [] (+ 1 2))\n"
                          "(def some-var 10)\n")
                     {:filename "test/duck_repled/tests.cljs"}))))

(def ^:dynamic *global-evaluator*
  (connect-sci!))

(def ^:dynamic *cljs-evaluator*
  (connect-sci!))

(def ^:dynamic *kind* :not-shadow)
