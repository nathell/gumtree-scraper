(ns gumtree
  (:require [clj-http.client        :as http]
            [clojure.string         :as string]
            [clojure.java.io        :as io]
            [clojure.xml            :as xml]
            [clojure-csv.core       :as csv])
  (:use     [net.cgrand.enlive-html :only [html-resource select emit*]])
  (:import  [java.text SimpleDateFormat]
            [java.util Date Locale]
            [java.io   StringReader])
  (:gen-class))

(defn funcall [f & args]
  (apply f args))

(defn parse-date [d]
  (.parse (SimpleDateFormat. "yyyy-MM-dd") d))

(defn format-date [& [d]]
  (.format (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss Z" Locale/ROOT)
           (condp funcall d
             nil? (Date.)
             string? (parse-date d)
             d)))

(def user-agent "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_6_8; de-at) AppleWebKit/533.21.1 (KHTML, like Gecko) Version/5.0.5 Safari/533.21.1")

(defn read-spreadsheet []
  (let [template "https://docs.google.com/spreadsheet/pub?key=%s&single=true&gid=0&output=csv"
        key (System/getProperty "spreadsheet.key")]
    (when-not key
      (binding [*out* *err*]
        (println "spreadsheet.key property not set, aborting")
        (flush))
      (System/exit 0))
    (slurp (format template key))))

(defn fetch [url]
  (html-resource (StringReader. (:body (http/get url {:headers {"User-Agent" user-agent}})))))

(defn get-page [query min max page]
  (try
    (let [query (.replace query " " "+")
          q (fetch (format "http://m.gumtree.com/f?q=%s&categoryId=2549&locationId=10000344&minPrice=%s&maxPrice=%s&page=%s" query min max page))]
      (map (comp :href :attrs) (drop-last 3 (select q [:li :a]))))
    (catch Throwable _ nil)))

(defn search [query min max]
  (apply concat (take-while identity (map get-page (repeat query) (repeat min) (repeat max) (iterate inc 0)))))

(defn get-item [item]
  (let [q (fetch (str "http://m.gumtree.com" item))]
    {:name (-> (select q [:title]) first :content first (string/replace #" \|.*$" ""))
     :date (-> (select q [:#startDate]) first :content first format-date)
     :content (.replace (apply str (emit* (select q [:#content])))
                        " href=\"/"
                        " href=\"http://m.gumtree.com/")}))

(defn vtransform [x]
  (if (string? x) x
      (let [[tag attrs & children] x]
        {:tag tag, :attrs attrs, :content (map vtransform children)})))

(defn escape-xml [e]
  (-> e (.replace "&" "&amp;") (.replace "<" "&lt;") (.replace ">" "&gt;") (.replace "\"" "&quot;") (.replace "'" "&apos;")))

(defn xml-emit-element [e]
  (if (instance? String e)
    (print (escape-xml e))
    (do
      (print (str "<" (name (:tag e))))
      (when (:attrs e)
	(doseq [attr (:attrs e)]
	  (print (str " " (name (key attr)) "='" (escape-xml (val attr)) "'"))))
      (if (:content e)
	(do
	  (print ">")
	  (doseq [c (:content e)]
	    (xml-emit-element c))
	  (print (str "</" (name (:tag e)) ">")))
	(print "/>")))))

(defn xml-emit [x]
  (println "<?xml version='1.0' encoding='UTF-8'?>")
  (xml-emit-element x)
  (println))

(defn get-items-for-query [query min max]
  (for [x (search query min max) :let [item (get-item x)]]
    [:item {}
     [:title {} (:name item)]
     [:description {} (:content item)]
     [:link {} (str "http://m.gumtree.com" x)]
     [:guid {} (str "http://m.gumtree.com" x)]
     [:pubDate {} (:date item)]]))

(defn make-rss []
  (with-out-str
    (xml-emit
     (vtransform
      [:rss {:version "2.0"}
       (apply vector
              :channel {}
              [:title {} "Tanio na Gumtree"]
              [:description {} "Tanio na Gumtree"]
              [:link {} "http://m.gumtree.com"]
              [:lastBuildDate {} (format-date)]
              [:pubDate {} (format-date)]
              (distinct
               (apply concat
                      (for [[what min max] (csv/parse-csv (read-spreadsheet))]
                        (get-items-for-query what min max)))))]))))

(defn -main []
  (spit "gumtree.rss" (make-rss)))
