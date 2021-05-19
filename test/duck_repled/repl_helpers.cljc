(ns duck-repled.repl-helpers
  (:require [duck-repled.repl-protocol :as repl]
            [promesa.core :as p]
            [clojure.edn :as edn]
            [sci.core :as sci]
            #?(:cljs ["net" :as net])))

(defn- treat-data [pending data]
  (let [full (:buffer (swap! pending update :buffer str data))
        [_ id result] (re-find #"(?s)__eval_result_beg\s+(.*?)\s+(.*?)\s+__eval_result_end" full)]
    ; (prn :FRAG full)
    ; (prn :RESULT id result)
    (when result
      (let [{:keys [id promise opts]} (:pending @pending)
            result (try
                      (edn/read-string {:default tagged-literal} result)
                      (catch #?(:cljs :default :clj Throwable) _
                        {(if (re-find #"^\{:result" result) :result :error)
                         result}))]
        (swap! pending dissoc :buffer :pending)
        (when promise
          (p/resolve! promise (assoc result :options opts)))))))

(defn- connect! [host port]
  #?(:clj nil
     :cljs
     (js/Promise.
      (fn [resolve fail]
        (let [pending (atom {})
              conn (. net createConnection port host)]
          (.on conn "connect" #(resolve {:conn conn :pending pending}))
          (.on conn "data" #(treat-data pending %)))))))

(def ^:private ex
  (str
   "#?("
   " :bb java.lang.Throwable"
   " :clj java.lang.Throwable"
   " :joker Error"
   " :cljs :default"
   " :cljr System.Exception"
   " :clje _)"))

(defn- eval! [conn pending command options]
  (p/do!
   (:pending @pending) ; IF there's something pending, wait

   (when-let [namespace (:namespace options)]
     ; (println "Sending NS\n" (str "(in-ns '" namespace ")\n"))
     (.write conn (str "(in-ns '" namespace ")\n")))
   (let [id (gensym "repl-eval-")
         promise (p/deferred)]
     (swap! pending assoc :pending {:id (str id)
                                    :promise promise
                                    :opts options})
     ; (println "******\nWILL EVAL:")
     ; (println (str "['__eval_result_beg"
     ;               " '" id
     ;               " (try {:result (do " command
     ;               ")} (catch " ex " t {:error t}))"
     ;               " '__eval_result_end]"))
     (.write conn (str "['__eval_result_beg"
                       " '" id
                       " (try {:result (do " command
                       ")} (catch " ex " t {:error t}))"
                       " '__eval_result_end]\n"))
     promise)))

(defn connect-socket! [host port]
  (p/let [{:keys [pending conn]} (connect! host port)
          repl (reify repl/Evaluator
                 (-evaluate [_ command options]
                    (eval! conn pending command options)))
          res (repl/eval repl ":ok")]
    (.write conn (str "(ns foo (:require [clojure.string :as str]))\n"
                      "(defn my-fun \"My doc\" [] (+ 1 2))\n"
                      "(def some-var 10)\n"))
    repl))

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

(defn prepare-repl [evaluator-fn]
  (p/let [evaluator (evaluator-fn)
          result
          (some-> evaluator
                  (repl/eval (str "(ns foo (:require [clojure.string :as str]))\n"
                                  "(defn my-fun \"My doc\" [] (+ 1 2))\n"
                                  "(def some-var 10)\n")
                             {:filename "test/duck_repled/tests.cljs"}))]
    evaluator))

(defonce ^:dynamic *global-evaluator*
  connect-sci!)

(defonce ^:dynamic *cljs-evaluator*
  connect-sci!)

(defonce ^:dynamic *kind* :sci)
