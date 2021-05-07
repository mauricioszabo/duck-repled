(ns duck-repled.repl-protocol
  (:refer-clojure :exclude [eval])
  (:require [promesa.core :as p]))

(defprotocol Evaluator
  (-evaluate
    [repl command options]
    "Evaluates a command (defined by `command` - a string) into the
REPL. Command can be multiple forms, and have comments, etc; it can
also be invalid Clojure code.

`options` is a map of `:namespace`, `:file`, `:row`, and `:col` (0-based).
Other keys are allowed, and are REPL-specific - for example, for Shadow-CLJS
you can use \"shadow remote API\" commands, etc.

Needs to return either a {:result <value>}, value being a valid EDN, or
{:error <error>}, also value being a valid EDN. Probably on Clojure, <error>
will always be an exception, and in JS, can be anything. You can also return
Promises / Futures, as long as they are Promesa-Compatible. For more information,
see Promesa's documentation (specifically, promesa.protocols)"))

(defn eval
  "Evaluates a command. `repl` is an instance of Evaluator, command is a
string with clojure command(s) to eval, and `options` is a map containing:

:namespace - a string that points to the namespace the command will run
:file - the current filename (some REPLs do not support this yet)
:row  - the current 0-based line (some REPLs do not support this yet)
:col  - the current 0-based column (some REPLs do not support this yet)
:pass - a map with parameters that will be included on the result, without any change
Other keys are allowed, and are REPL-specific - for example, for Shadow-CLJS can
use additional keys to change JS targets, for example

Returns {:succes <edn>} or {:error <edn>}, merged with keys passed to `:pass` parameter"
  ([repl command] (eval repl command {}))
  ([repl command options]
   (let [result (-evaluate repl command (dissoc options :pass))
         pass (:pass options {})]
     (if (p/promise? result)
       (p/then result #(merge pass %))
       (merge pass result)))))
