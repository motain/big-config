(ns big-config.step-fns
  (:require
   [big-config :as bc]
   [big-config.core :refer [->step-fn]]))

(defn exit-with-code [n]
  (shutdown-agents)
  (flush)
  (System/exit n))

(defn ->exit-step-fn [end]
  (->step-fn {:after-f (fn [step {:keys [::bc/env ::bc/exit]}]
                         (when (and (= step end)
                                    (not= env :repl))
                           (exit-with-code exit)))}))

(def tap-step-fn
  (let [f (fn [label step opts]
            (tap> [step label opts]))]
    (->step-fn {:before-f (partial f :before)
                :after-f (partial f :after)})))

(defn log-step-fn [f step opts]
  (->> (update opts ::bc/steps (fnil conj []) step)
       (f step)))
