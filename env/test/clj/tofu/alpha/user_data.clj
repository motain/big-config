(ns tofu.alpha.user-data
  (:require
   [clojure.java.io :as io]
   [selmer.parser :refer [<<]]))

(def text "Hello world!")

(defn ^:export invoke []
  #_{:clj-kondo/ignore [:unused-binding]}
  (let [text text]
    (<< (slurp (io/resource "user_data.sh")))))
