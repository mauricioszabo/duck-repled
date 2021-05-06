(ns duck-repled.editor-helpers
  (:require [clojure.string :as str]
            [cljs.reader :as edn]
            [cljs.tools.reader :as reader]
            ; [rewrite-clj.zip.move :as move]
            [rewrite-clj.zip :as zip]
            [rewrite-clj.zip.base :as zip-base]
            [rewrite-clj.node :as node]
            ; [rewrite-clj.reader :as clj-reader]
            [clojure.tools.reader.reader-types :as r]
            [rewrite-clj.parser :as parser]))

(defn- simple-read [str]
  (reader/read-string {:default (fn [_ res] res) :read-cond :allow} str))

(defn- parse-reader [reader]
  (try
    (let [parsed (parser/parse reader)]
      (when parsed
        (cond
          (node/whitespace-or-comment? parsed) :whitespace

          (instance? rewrite-clj.node.uneval/UnevalNode parsed)
          (->> parsed :children (remove node/whitespace-or-comment?) first)

          :else parsed)))
    (catch :default _
      (r/read-char reader)
      :whitespace)))

(defn top-blocks [code]
  (let [reader (r/indexing-push-back-reader code)]
    (loop [sofar []]
      (let [parsed (parse-reader reader)]
        (case parsed
          :whitespace (recur sofar)
          nil sofar
          (let [as-str (node/string parsed)
                {:keys [row col end-row end-col]} (meta parsed)]
            (recur (conj sofar [[[(dec row) (dec col)]
                                 [(dec end-row) (- end-col 2)]]
                                as-str]))))))))

(defn ns-range-for
  "Gets the current NS range (and ns name) for the current code, considering
that the cursor is in row and col (0-based)"
  [top-levels [row col]]
  (let [before-selection? (fn [[[[_ _] [erow ecol]] _]]
                            (or (and (= erow row) (<= col ecol))
                                (< erow row)))
        is-ns? #(and (list? %) (some-> % first (= 'ns)))
        read (memoize #(try (simple-read %) (catch :default _ nil)))
        find-ns-for (fn [top-blocks] (->> top-blocks
                                          (map #(update % 1 read))
                                          (filter #(-> % peek is-ns?))
                                          (map #(update % 1 second))
                                          first))]
    (or (->> top-levels
             (take-while before-selection?)
             reverse
             find-ns-for)
        (->> top-levels
             (drop-while before-selection?)
             find-ns-for))))

(defn- current-var* [zipped row col]
  (let [node (-> zipped
                 (zip/find-last-by-pos {:row (inc row) :col (inc col)})
                 zip/node)]
    (when (and node (-> node node/whitespace-or-comment? not))
      (let [{:keys [row col end-row end-col]} (meta node)]
        [[[(dec row) (dec col)] [(dec end-row) (- end-col 2)]]
         (node/string node)]))))

(defn- zip-from-code [code]
  (let [reader (r/indexing-push-back-reader code)
        nodes (->> (repeatedly #(try
                                  (parser/parse reader)
                                  (catch :default _
                                    (r/read-char reader)
                                    (node/whitespace-node " "))))
                   (take-while identity)
                   (doall))
        all-nodes (with-meta
                    (node/forms-node nodes)
                    (meta (first nodes)))]
    (-> all-nodes zip-base/edn)))

(defn current-var [code [row col]]
  (let [zipped (zip-from-code code)]
    (or (current-var* zipped row col)
        (current-var* zipped row (dec col)))))
