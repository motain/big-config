(ns big-config.aero
  (:require
   [aero.core :as aero]
   [big-config :as bc]
   [big-config.utils :refer [deep-merge]]
   [clojure.string :as str]
   [clojure.walk :as walk]))

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
  [{:keys [::config ::module ::profile] :as opts}]
  (let [config (-> (aero/read-config config {:profile (or profile :default)})
                   module)]
    (loop [config (deep-merge config opts)
           done (atom true)
           iteration 0]
      (let [config (walk/prewalk #(if (and (sequential? %)
                                           (= (first %) ::join))
                                    (aero-join config (rest %) done)
                                    %) config)]
        (if (> iteration 100)
          (merge config
                 {::bc/exit 1
                  ::bc/err "Too many iteration in `big-config.aero/read-module`"})
          (if @done
            (merge config
                   {::bc/exit 0
                    ::bc/err nil})
            (recur config (atom true) (inc iteration))))))))
