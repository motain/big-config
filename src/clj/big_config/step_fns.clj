(ns big-config.step-fns
  (:require
   [big-config :as bc]
   [bling.core :refer [bling]]))

(defn exit-with-code [n]
  (shutdown-agents)
  (flush)
  (System/exit n))

(defn exit-step-fn [end f step opts]
  (let [{:keys [::bc/env ::bc/exit] :as opts} (f step opts)]
    (if (= step end)
      (case env
        :shell (exit-with-code exit)
        :repl opts)
      opts)))

(defn trace-step-fn [f step opts]
  (binding [*out* *err*]
    (println (bling [:blue.bold step])))
  (f step (update opts ::bc/steps (fnil conj []) step)))
