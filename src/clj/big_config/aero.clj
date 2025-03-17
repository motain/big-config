(ns big-config.aero
  (:require
   [aero.core :as aero]
   [big-config :as bc]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn ready?
  "All elements resolves to a string"
  [config ready x]
  (if (or (string? x)
          (and (keyword? x) (or (string? (x config))
                                (keyword? (x config)))))
    ready
    false))

(defn seq->str
  "Convert the seq to a str"
  [config xs]
  (->> xs
       (map (fn [e] (if (keyword? e)
                      (name (e config))
                      e)))
       (str/join "")))

(defn aero-join
  "Template fn to join an array of string and variables to a string"
  [config xs done]
  (let [ready (reduce (partial ready? config) true xs)
        xs (if ready
             (seq->str config xs)
             (conj xs ::join))]
    (reset! done (and @done ready))
    xs))

(defn read-module
  "Step to read the opts from file or resource"
  [{:keys [::resource ::module ::profile]}]
  (let [config (-> (aero/read-config (io/resource resource) {:profile profile})
                   module
                   (merge {::resource resource
                           ::module module
                           ::profile profile}))]
    (loop [config config
           done (atom true)
           iteration 0]
      (let [config (update-vals config (fn [v]
                                         (if (and (sequential? v)
                                                  (= (first v) ::join))
                                           (aero-join config (rest v) done)
                                           v)))]
        (if @done
          (merge config
                 {::bc/exit 0
                  ::bc/err nil})
          (recur config (atom true) (inc iteration)))))))
