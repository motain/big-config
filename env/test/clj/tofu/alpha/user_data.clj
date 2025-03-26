(ns tofu.alpha.user-data
  (:require
   [selmer.parser :refer [<<]]
   [clojure.java.io :as io]))

(def text "Hello world!")

(defn ^:export invoke []
  #_{:clj-kondo/ignore [:unused-binding]}
  (let [text text]
    (<< (slurp (io/resource "user_data.sh")))))
