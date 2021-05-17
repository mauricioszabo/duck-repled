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
  {::pco/output [:definition/filename]}

  (when (file-exists? (:file meta))
    {:definition/filename (-> meta :file norm-result)}))

(def resolvers [join-paths-resolver file-exists-resolver position-resolver
                existing-filename])
