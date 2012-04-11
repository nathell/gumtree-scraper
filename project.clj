(defproject gumtree "0.1"
  :description "Gumtree RSS generator."
  :main gumtree
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [paddleguru/enlive "1.2.0-alpha1" :exclusions [org.ccil.cowan.tagsoup/tagsoup]]
                 [clojure-csv "2.0.0-alpha1"]
                 [org.clojars.nathell/tagsoup "1.2.1"]
                 [clj-http "0.3.3"]])
