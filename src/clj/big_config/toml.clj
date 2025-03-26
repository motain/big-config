(ns big-config.toml
  (:refer-clojure :exclude [read-string])
  (:require
   [babashka.process :as process]
   [cheshire.core :as json]
   [clojure.java.io :as io]))

(defn read-string [s]
  (-> (process/shell {:in s :out :string} "yj -t")
      :out
      (json/parse-string true)))

(comment
  (-> (slurp (io/resource "example.toml"))
      read-string))
