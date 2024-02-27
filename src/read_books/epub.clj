(ns read-books.epub
  (:import [java.util.zip ZipFile ZipEntry]
           [java.io File])
  (:require [clojure.data.xml :as xml]
            [clojure.string :as str]
            [hiccup2.core :as h]))

(def ^:const container-path "META-INF/container.xml")
(def ^:const mimetype-path "mimetype")
(def ^:const expected-mimetype "application/epub+zip")

;; ZIP utils
(defmulti parse-entry
  "Parse a `ZipEntry`.
  - returns an xml map file extension is xml or opf
  - returns a string otherwise"
  (fn [^ZipEntry entry]
    (last (str/split (.getName entry) #"\."))))
(defmethod parse-entry "xml"
  [entry ^ZipFile epub]
  (->> entry (.getInputStream epub) xml/parse))
(defmethod parse-entry "opf"
  [entry ^ZipFile epub]
  (->> entry (.getInputStream epub) xml/parse))
(defmethod parse-entry :default
  [entry ^ZipFile epub]
  (->> entry (.getInputStream epub) slurp))

(defn find-entry
  "Finds an entry by path in epub archive and parses it."
  [^ZipFile epub entry-path]
  (let [entry (first
               (filter
                #(= (.getName %) entry-path)
                (enumeration-seq (.entries epub))))]
    (when-not entry
      (throw (ex-info "Entry not found in epub file"
                      {:searched-entry entry-path})))
    (parse-entry entry epub)))

;; XML utils
(defn find-first-xml-key
  "Find the fist key in an XML list."
  [xml key]
  (->> xml
       (filter #(= (:tag %) key))
       first :content))

(defn get-attr
  "Return the attribute under a key"
  [xml key]
  (get-in xml [:attrs key]))

;; Container
(defn list-rootfiles-path
  "List rootfiles from container."
  [epub]
  (let [container-xml (find-entry epub container-path)
        rootfiles (-> container-xml
                      :content
                      (find-first-xml-key :rootfiles))]
    (map #(get-attr % :full-path) rootfiles)))

;; Rootfile
(defn rootfile-title
  "Find a rootfile title"
  [rootfile-xml]
  (-> rootfile-xml
      :content
      (find-first-xml-key :metadata)
      (find-first-xml-key :title)))

(defn rootfile-itemrefs
  [rootfile-xml]
  (-> rootfile-xml
      :content
      (find-first-xml-key :spine)))

(defn rootfile-content
  [rootfile-xml]
  (let [itemrefs (rootfile-itemrefs rootfile-xml)]
    (map #(get-attr % :idref) itemrefs)))

;; Item
(defn item-find-by-idref
  [idref rootfile]
  (let [manifest-items (-> rootfile
                           :content
                           (find-first-xml-key :manifest))]
    (first (filter #(= (get-attr % :id) idref) manifest-items))))

(defn item-find-href
  [idref rootfile]
  (get-attr (item-find-by-idref idref rootfile) :href))

(defn item-as-html
  [idref rootfile]
  (let [href (item-find-href idref rootfile)]
    (str (h/html [:a {:href href} idref]))))

;; EPUB
(defn load-epub
  "Load a new epub file and check for the appropriate mimetype."
  [^File file]
  (let [zip-file (ZipFile. file)
        mimetype-entry (find-entry zip-file mimetype-path)]
    (when-not (= mimetype-entry expected-mimetype)
      (throw (ex-info "File is not of type EPUB"
                      {:expected-mimetype expected-mimetype
                       :found-mimetype mimetype-entry})))
    zip-file))

(defn list-content-as-html
  [epub]
  (let [rootfiles-path (list-rootfiles-path epub)
        rootfiles (map #(find-entry epub %) rootfiles-path)]
    (pmap (fn [rootfile]
            (map #(item-as-html % rootfile)
                 (rootfile-content rootfile)))
          rootfiles)))
