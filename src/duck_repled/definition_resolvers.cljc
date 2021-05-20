(ns duck-repled.definition-resolvers
  (:require [clojure.string :as str]
            [duck-repled.connect :as connect]
            [com.wsscode.pathom3.connect.operation :as pco]
            [duck-repled.repl-protocol :as repl]
            [duck-repled.template :refer [template]]
            [promesa.core :as p]
            #?(:cljs ["fs" :refer [existsSync statSync]])
            #?(:cljs ["path" :refer [join]])
            #?(:cljs ["os" :refer [platform]]))
  #?(:clj (:import [java.io File]
                   [java.nio.file FileSystems])))

(defn- join-paths [paths]
  #?(:cljs (apply join paths)
     :clj (if (empty? paths)
            "."
            (let [[fst & rst] paths]
              (-> (FileSystems/getDefault)
                  (.getPath fst (into-array rst))
                  str)))))

(defn- file-exists? [path]
  #?(:clj (.isFile (File. path))
     :cljs (and (existsSync path)
                (.isFile (statSync path)))))

(connect/defresolver join-paths-resolver [{:keys [:file/path]}]
  {:file/filename (join-paths path)})

(connect/defresolver file-exists-resolver [{:keys [:file/filename]}]
  {:file/exists? (file-exists? filename)})

(connect/defresolver position-resolver [{:keys [:var/meta]}]
  {::pco/output [:definition/row :definition/col]}

  (when-let [line (:line meta)]
    (cond-> {:definition/row (-> meta :line dec)}
            (:column meta) (assoc :definition/col (-> meta :column dec)))))

(defn- norm-result [file-name]
  (let [os #?(:clj (System/getProperty "os.name")
              :cljs (platform))]
    (cond-> file-name
            (and (re-find #"(?i)^win" os))
            (str/replace-first #"^/" ""))))

(connect/defresolver existing-filename [{:keys [:var/meta]}]
  {::pco/output [:definition/filename]
   ::pco/priority 1}

  (when (file-exists? (:file meta))
    {:definition/filename (-> meta :file norm-result)}))

(defn- read-jar [clj jar-file-name]
  (let [[jar path] (str/split jar-file-name #"!/" 2)
        jar (clojure.string/replace-first jar #"file:" "")
        t `(let [jar-file# (java.util.jar.JarFile. ::jar)
                 ba# (java.io.ByteArrayOutputStream.)
                 is# (.getInputStream jar-file# (.getJarEntry jar-file# ::path))]
             (clojure.java.io/copy is# ba#)
             (java.lang.String. (.toByteArray ba#)))
        code (template t {::jar jar ::path path})]
    (repl/eval clj code)))

(connect/defresolver clojure-filename [{:keys [:repl/evaluator :var/meta
                                               :repl/kind :repl/clj]}]
  {::pco/input [:repl/evaluator :var/meta :repl/kind (pco/? :repl/clj)]
   ::pco/output [:definition/filename :definition/file-contents]}

  (when-let [repl (case kind
                   :clj evaluator
                   :cljs clj
                   nil)]
    (p/let [code (template `(do
                              (require 'clojure.java.io)
                              (some->> :file
                                       (.getResource (clojure.lang.RT/baseLoader))
                                       .getPath))
                           (select-keys meta [:file]))
            {:keys [result]} (repl/eval repl code)
            filename (norm-result result)]
      (if (re-find #"\.jar!/" filename)
        (p/let [{:keys [result]} (read-jar repl filename)]
          {:definition/filename filename
           :definition/file-contents result})
        {:definition/filename filename}))))

(connect/defresolver file-from-clr [{:keys [:repl/evaluator :var/meta]}]
  {::pco/output [:definition/filename]}
  (p/let [code (template `(some-> ::file
                                  clojure.lang.RT/FindFile
                                  str)
                         {::file (:file meta)})
          {:keys [result]} (repl/eval evaluator code)]
    (when result
      {:definition/filename (norm-result result)})))

(def resolvers [join-paths-resolver file-exists-resolver position-resolver
                existing-filename clojure-filename file-from-clr])
