(ns duck-repled.editor-helpers
  (:require [clojure.string :as str]
            ; [cljs.reader :as edn]
            [clojure.tools.reader :as reader]
            [rewrite-clj.zip.move :as move]
            [rewrite-clj.zip :as zip]
            [rewrite-clj.zip.base :as zip-base]
            [rewrite-clj.node :as node]
            [clojure.tools.reader.reader-types :as r]
            [rewrite-clj.parser :as parser]

            #?(:cljs [rewrite-clj.node.uneval :refer [UnevalNode]])
            #?(:cljs [rewrite-clj.node.reader-macro :refer [ReaderMacroNode DerefNode]])
            #?(:cljs [rewrite-clj.node.fn :refer [FnNode]])
            #?(:cljs [rewrite-clj.node.quote :refer [QuoteNode]]))
  #?(:clj (:import [rewrite_clj.node.uneval UnevalNode]
                   [rewrite_clj.node.reader_macro ReaderMacroNode DerefNode]
                   [rewrite_clj.node.fn FnNode]
                   [rewrite_clj.node.quote QuoteNode])))

(defn- simple-read [str]
  (reader/read-string {:default (fn [_ res] res) :read-cond :allow} str))

(defn- parse-reader [reader]
  (try
    (let [parsed (parser/parse reader)]
      (when parsed
        (cond
          (node/whitespace-or-comment? parsed) :whitespace

          (instance? UnevalNode parsed)
          (->> parsed :children (remove node/whitespace-or-comment?) first)

          :else parsed)))
    (catch #?(:clj Throwable :cljs :default) _
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
        read (memoize #(try
                         (simple-read %)
                         (catch #?(:clj Throwable :cljs :default) _ nil)))
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
                                  (catch #?(:clj Throwable :cljs :default) _
                                    (r/read-char reader)
                                    (node/whitespace-node " "))))
                   (take-while identity)
                   (doall))
        all-nodes (with-meta
                    (node/forms-node nodes)
                    (meta (first nodes)))]
    (-> all-nodes (zip-base/edn {:track-position? true}))))

(defn current-var [code [row col]]
  (let [zipped (zip-from-code code)]
    (or (current-var* zipped row col)
        (current-var* zipped row (dec col)))))


(defn in-range? [{:keys [row col end-row end-col]} {r :row c :col}]
  (and (>= r row)
       (<= r end-row)
       (if (= r row) (>= c col) true)
       (if (= r end-row) (<= c end-col) true)))

(defn top-block-for
  "Gets the top-level from the code (a string) to the current row and col (0-based)"
  [tops [row col]]
  (let [in-range? (fn [[[[b-row b-col] [e-row e-col]]]]
                    (or (and (<= b-row row) (< row e-row))
                        (and (<= b-row row e-row)
                             (or (<= b-col col e-col)
                                 (<= b-col (dec col) e-col)))))]
    (->> tops (filter in-range?) first)))

(defn- find-inners-by-pos
  "Find last node (if more than one node) that is in range of pos and
  satisfying the given predicate depth first from initial zipper
  location."
  [zloc pos]
  (->> zloc
       (iterate zip/next)
       (take-while identity)
       (take-while (complement move/end?))
       (filter #(in-range? (-> % zip/node meta) pos))))

(defn- reader-tag? [node]
  (when node
    (or (instance? ReaderMacroNode node)
        (instance? FnNode node)
        (instance? QuoteNode node)
        (instance? DerefNode node))))

(defn- filter-forms [nodes]
  (when nodes
    (let [valid-tag? (comp #{:vector :list :map :set :quote} :tag)]
      (->> nodes
           (map zip/node)
           (partition-all 2 1)
           (map (fn [[fst snd]]
                  (cond
                    (reader-tag? fst) fst
                    (-> fst :tag (= :list) (and snd (reader-tag? snd))) snd
                    (valid-tag? fst) fst)))
           (filter identity)
           first))))

(defn block-for
  "Gets the current block from the code (a string) to the current row and col (0-based)"
  [code [row col]]
  (let [node-block (-> code
                       zip-from-code
                       (find-inners-by-pos {:row (inc row) :col (inc col)})
                       reverse
                       filter-forms)
        {:keys [row col end-row end-col]} (some-> node-block meta)]
    (when node-block
      [[[(dec row) (dec col)] [(dec end-row) (- end-col 2)]]
       (node/string node-block)])))

(defn text-in-range [text [[row1 col1] [row2 col2]]]
  (let [lines (str/split-lines text)
        rows-offset (- (min row2 (count lines)) row1)]
    (-> lines
        (subvec row1 (min (count lines) (inc row2)))
        (update 0 #(str/join "" (drop col1 %)))
        (update rows-offset #(str/join "" (take (inc (if (zero? rows-offset)
                                                       (- col2 col1)
                                                       col2))
                                                %)))
        (->> (str/join "\n")))))
