(ns untangled.i18n.util
  (:require [clojure.string :as str]
            [clojure.pprint :as pp]))



(defn parse-po [acc line]
  (let [context (last (re-matches #"^msgctxt \"(.*)\"" line))
        msgid (last (re-matches #"^msgid \"(.*)\"" line))
        translation (last (re-matches #"^msgstr \"(.*)\"" line))]
    (cond
      context (assoc-in acc [:seen :context] context)
      msgid (assoc-in acc [:seen :id] msgid)
      translation (let [empty-seen {:context "" :id ""}
                        js-key (str (get-in acc [:seen :context]) "|" (get-in acc [:seen :id]))
                        js-obj (assoc (:cljs-obj acc) js-key translation)]
                    (assoc acc :cljs-obj js-obj :seen empty-seen))
      true acc)
    ))

(defn map-po-to-translations [fname]
  (let [lines (str/split-lines (slurp fname))]
    (:cljs-obj (reduce #(parse-po %1 %2)
                       {:seen {:context "" :id "" :id-value-acc [] :trans-value-acc []} :cljs-obj {}} lines))
    ))

(defn wrap-with-swap [& {:keys [locale translation]}]
  (let [trans-namespace (symbol (str "untangled.translations." locale))
        ns-decl (pp/write (list 'ns trans-namespace (list :require 'untangled.i18n.core)) :stream nil)
        trans-def (pp/write (list 'def 'translations translation) :stream nil)
        swap-decl (pp/write (list 'swap! 'untangled.i18n.core/*loaded-translations*
                                  (list 'fn '[x] (list 'assoc 'x locale 'translations))) :stream nil)
        comment ";; This file was generated by untangled's i18n leiningen plugin."]
    (str/join "\n\n" [ns-decl comment trans-def swap-decl])))

(defn write-cljs-translation-file [fname translations-string]
  (spit fname translations-string)
  )


(defn group-chunks [line]
  (reduce (fn [acc line]
            (if (re-matches #"^msg.*" line)
              (conj acc [line])
              (update-in acc [(dec (count acc))] conj line)))
          [] line))

(defn group-translations [fname]
  (let [fstring (slurp fname)
        trans-chunks (rest (clojure.string/split fstring #"(?ms)\n\n"))
        grouped-chunks (map clojure.string/split-lines trans-chunks)
        comment? #(re-matches #"^#.*" %)
        uncommented-chunks (map #(remove comment? %) grouped-chunks)

        keyed-chunk #(reduce (fn [acc line]
                               (if (re-matches #"^msg.*" line)
                                 (conj acc [line])
                                 (update-in acc [(dec (count acc))] conj line)))
                             [] %)

        keyed-chunks (map keyed-chunk uncommented-chunks)

        ] (if (empty? keyed-chunks) nil keyed-chunks)))
